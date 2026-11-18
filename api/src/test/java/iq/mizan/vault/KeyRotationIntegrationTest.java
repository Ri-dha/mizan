package iq.mizan.vault;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** ADR e2e-4: a shared key is rotated once anyone holding it has lost access, and only then. */
class KeyRotationIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final String HOUSEHOLD = "HOUSEHOLD";
    private static final String REPORT = "REPORT";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;
    private UUID ownerId;
    private ApiClient.Session second;
    private UUID secondId;
    private ApiClient.Session third;
    private UUID thirdId;

    @BeforeEach
    void ownerWithTwoMembersHoldingBothKeys() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        ownerId = id(owner);
        second = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        secondId = id(second.accessToken());
        third = api.register(accounts.thirdEmail(), accounts.thirdPassword(), "Sara");
        thirdId = id(third.accessToken());
        second = join(second, "MEMBER");
        third = join(third, "MEMBER");
        for (String kind : List.of(HOUSEHOLD, REPORT)) {
            for (UUID member : List.of(ownerId, secondId, thirdId)) {
                assertThat(grant(owner, member, kind, 1)).hasStatus(HttpStatus.OK);
            }
        }
    }

    @Test
    void nothingIsDueWhileEveryoneHoldingTheKeyStillHasAccess() {
        assertThat(due(owner, HOUSEHOLD)).isFalse();
        assertThat(version(owner, HOUSEHOLD)).isEqualTo(1);
        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId, secondId, thirdId)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void removingAMemberMakesBothKeysDueAndARotationEndsTheirAccess() {
        assertThat(api.delete("/api/v1/households/current/members/" + thirdId, owner)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(due(owner, HOUSEHOLD)).isTrue();
        assertThat(due(owner, REPORT)).isTrue();

        assertThat(rotate(second.accessToken(), HOUSEHOLD, 2, ownerId, secondId)).hasStatus(HttpStatus.OK);

        assertThat(due(owner, HOUSEHOLD)).isFalse();
        assertThat(version(owner, HOUSEHOLD)).isEqualTo(2);
        assertThat(due(owner, REPORT)).isTrue();
        assertThat(grantsOf(thirdId, HOUSEHOLD)).isZero();
        assertThat(grantsOf(thirdId, REPORT)).isEqualTo(1);
        assertThat(heldVersions(owner, HOUSEHOLD)).containsExactly(1, 2);
    }

    @Test
    void aRotationMustNameEveryRemainingHolderAndTheNextVersion() {
        api.delete("/api/v1/households/current/members/" + thirdId, owner);

        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId, secondId, thirdId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId, secondId, secondId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(rotate(owner, HOUSEHOLD, 3, ownerId, secondId)).hasStatus(HttpStatus.CONFLICT);
        assertThat(rotate(third.accessToken(), HOUSEHOLD, 2, ownerId, secondId)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(version(owner, HOUSEHOLD)).isEqualTo(1);
    }

    @Test
    void theSecondOfTwoRotationsToTheSameVersionLoses() {
        api.delete("/api/v1/households/current/members/" + thirdId, owner);

        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId, secondId)).hasStatus(HttpStatus.OK);
        MvcTestResult late = rotate(second.accessToken(), HOUSEHOLD, 2, ownerId, secondId);

        assertThat(late).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.body(late).get("keyVersion").asInt()).isEqualTo(2);
        assertThat(count("select count(*) from household_key_grant where key_kind = 'HOUSEHOLD' and key_version = 2")).isEqualTo(2);
    }

    @Test
    void afterARotationAPushSealedWithTheOldVersionIsRefused() {
        api.delete("/api/v1/households/current/members/" + thirdId, owner);
        rotate(owner, HOUSEHOLD, 2, ownerId, secondId);

        MvcTestResult stale = push(second.accessToken(), "SHARED", seal("h", 1));
        assertThat(stale).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.body(stale).get("keyVersion").asInt()).isEqualTo(2);
        assertThat(push(second.accessToken(), "SHARED", seal("h", 2))).hasStatus(HttpStatus.OK);
        assertThat(push(second.accessToken(), "PRIVATE", seal("u", 1))).hasStatus(HttpStatus.OK);
    }

    @Test
    void someoneJoiningAfterARotationCanBeGrantedEveryVersion() {
        api.delete("/api/v1/households/current/members/" + thirdId, owner);
        rotate(owner, HOUSEHOLD, 2, ownerId, secondId);
        third = join(third, "MEMBER");

        assertThat(grant(owner, thirdId, HOUSEHOLD, 1)).hasStatus(HttpStatus.OK);
        assertThat(grant(owner, thirdId, HOUSEHOLD, 2)).hasStatus(HttpStatus.OK);
        assertThat(heldVersions(third.accessToken(), HOUSEHOLD)).containsExactly(1, 2);
        assertThat(due(owner, HOUSEHOLD)).isFalse();
    }

    @Test
    void leavingMakesBothKeysDue() {
        assertThat(api.post("/api/v1/households/current/leave", third.accessToken(), null)).hasStatus(HttpStatus.OK);

        assertThat(due(owner, HOUSEHOLD)).isTrue();
        assertThat(due(owner, REPORT)).isTrue();
    }

    @Test
    void aRoleThatMayNoLongerHoldAKeyMakesOnlyThatKeyDue() {
        assertThat(api.patch("/api/v1/households/current/members/" + thirdId, owner, Map.of("role", "ADVISOR"))).hasStatus(HttpStatus.OK);

        assertThat(due(owner, HOUSEHOLD)).isTrue();
        assertThat(due(owner, REPORT)).isFalse();
    }

    @Test
    void lapsedAccessMakesTheKeysDue() {
        jdbc.update("update membership set expires_at = now() - interval '1 minute' where user_id = ? and household_id = ?", thirdId, householdId());

        assertThat(due(owner, HOUSEHOLD)).isTrue();
        assertThat(due(owner, REPORT)).isTrue();
    }

    @Test
    void aDeletedAccountLeavesItsGrantsBehindAndMakesTheKeysDue() {
        jdbc.update("delete from app_user where id = ?", thirdId);

        assertThat(count("select count(*) from household_key_grant where user_id is null")).isEqualTo(2);
        assertThat(due(owner, HOUSEHOLD)).isTrue();
        assertThat(rotate(owner, HOUSEHOLD, 2, ownerId, secondId)).hasStatus(HttpStatus.OK);
        assertThat(count("select count(*) from household_key_grant where user_id is null and key_kind = 'HOUSEHOLD'")).isZero();
    }

    private boolean due(String token, String kind) {
        return state(token, kind).get("rotationDue").asBoolean();
    }

    private int version(String token, String kind) {
        return state(token, kind).get("currentVersion").asInt();
    }

    private JsonNode state(String token, String kind) {
        for (JsonNode entry : api.body(api.get("/api/v1/keys/household/state", token))) {
            if (kind.equals(entry.get("keyKind").asString())) {
                return entry;
            }
        }
        throw new AssertionError("No " + kind + " key state");
    }

    private List<Integer> heldVersions(String token, String kind) {
        List<Integer> versions = new ArrayList<>();
        for (JsonNode grant : api.body(api.get("/api/v1/keys/me", token)).get("grants")) {
            if (kind.equals(grant.get("keyKind").asString())) {
                versions.add(grant.get("keyVersion").asInt());
            }
        }
        return versions;
    }

    private MvcTestResult rotate(String token, String kind, int version, UUID... recipients) {
        List<Map<String, String>> grants = new ArrayList<>();
        for (UUID recipient : recipients) {
            grants.add(Map.of("userId", recipient.toString(), "wrappedKey", "wrapped-" + kind + "-" + version + "-" + recipient));
        }
        return api.post("/api/v1/keys/household/rotate", token, Map.of("keyKind", kind, "keyVersion", version, "grants", grants));
    }

    private MvcTestResult grant(String token, UUID recipient, String kind, int version) {
        return api.post("/api/v1/keys/household/grants", token, List.of(Map.of("userId", recipient.toString(),
                "keyKind", kind, "keyVersion", version, "wrappedKey", "wrapped-" + kind + "-" + version + "-" + recipient)));
    }

    private ApiClient.Session join(ApiClient.Session member, String role) {
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", role)));
        api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", member.accessToken(), null));
        return api.refresh(member.refreshToken());
    }

    private MvcTestResult push(String token, String visibility, String sealedName) {
        String device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("visibility", visibility);
        fields.put("name", sealedName);
        Map<String, String> clocks = new HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, CLOCK));
        return api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops", List.of(Map.of(
                "opId", UUID.randomUUID().toString(), "table", "cash_account", "rowId", UUID.randomUUID().toString(), "fields", fields, "clocks", clocks))));
    }

    /** Test ciphertext in the e1 format, under the given key letter and version. */
    private static String seal(String key, int version) {
        String body = "iv-and-ciphertext|Wallet|".repeat(2);
        return "e1." + key + "." + version + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private int grantsOf(UUID userId, String kind) {
        return jdbc.queryForObject("select count(*) from household_key_grant where user_id = ? and key_kind = ?", Integer.class, userId, kind);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private UUID householdId() {
        return jdbc.queryForObject("select household_id from membership where user_id = ? and role = 'OWNER'", UUID.class, ownerId);
    }

    private UUID id(String token) {
        return UUID.fromString(api.body(api.get("/api/v1/me", token)).get("id").asString());
    }
}
