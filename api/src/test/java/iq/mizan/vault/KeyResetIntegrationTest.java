package iq.mizan.vault;

import static org.assertj.core.api.Assertions.assertThat;

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

/** ADR e2e-3: starting over removes only what nobody can open any more, and lets the person back in. */
class KeyResetIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;
    private UUID ownerId;
    private ApiClient.Session other;
    private UUID otherId;

    @BeforeEach
    void ownerAndAnotherAccount() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        ownerId = id(owner);
        other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        otherId = id(other.accessToken());
    }

    @Test
    void aMemberWhoStartsOverLosesTheirPrivateRecordsButNotTheHouseholds() {
        grant(owner, ownerId, "HOUSEHOLD");
        String member = join("MEMBER");
        grant(owner, otherId, "HOUSEHOLD");
        push(owner, "PRIVATE");
        UUID sharedRow = push(member, "SHARED");
        push(member, "PRIVATE");
        push(member, "PRIVATE");

        JsonNode preview = api.body(api.get("/api/v1/keys/me/reset-preview", member));
        assertThat(preview.get("privateRecords").asLong()).isEqualTo(2);
        assertThat(soleHolders(preview)).isZero();

        assertThat(reset(member, "RESET")).hasStatus(HttpStatus.OK);

        assertThat(count("select count(*) from vault_record where owner_id = ? and visibility = 'PRIVATE'", otherId)).isZero();
        assertThat(count("select count(*) from vault_record where owner_id = ? and visibility = 'PRIVATE'", ownerId)).isEqualTo(1);
        assertThat(count("select count(*) from vault_record where id = ?", sharedRow)).isEqualTo(1);
        assertThat(count("select count(*) from household_key_grant where user_id = ?", otherId)).isZero();
        assertThat(api.body(api.get("/api/v1/keys/me", member)).get("account").get("publicKey").asString()).isEqualTo("new-public-key");

        JsonNode members = api.body(api.get("/api/v1/keys/household/members", owner));
        for (JsonNode m : members) {
            if (otherId.toString().equals(m.get("userId").asString())) {
                assertThat(m.get("publicKey").asString()).isEqualTo("new-public-key");
                assertThat(m.get("holds")).isEmpty();
            }
        }
        assertThat(grant(owner, otherId, "HOUSEHOLD")).hasStatus(HttpStatus.OK);
        assertThat(count("select count(*) from audit_entry where action = 'KEYS_RESET' and actor_id = ?", otherId)).isEqualTo(1);
    }

    @Test
    void theLastKeyHolderStartingOverRestartsTheHousehold() {
        grant(owner, ownerId, "HOUSEHOLD");
        grant(owner, ownerId, "REPORT");
        push(owner, "SHARED");
        push(owner, "SHARED");

        JsonNode preview = api.body(api.get("/api/v1/keys/me/reset-preview", owner));
        assertThat(soleHolders(preview)).isEqualTo(1);
        assertThat(preview.get("households").get(0).get("sharedRecordsLost").asLong()).isEqualTo(2);

        reset(owner, "RESET");
        UUID household = jdbc.queryForObject("select household_id from membership where user_id = ?", UUID.class, ownerId);
        assertThat(count("select count(*) from vault_record where household_id = ?", household)).isZero();
        assertThat(count("select count(*) from household_key_grant where household_id = ?", household)).isZero();
        assertThat(count("select count(*) from household_key_state where household_id = ?", household)).isZero();
        assertThat(grant(owner, ownerId, "HOUSEHOLD")).hasStatus(HttpStatus.OK);
        assertThat(count("select count(*) from household_key_state where household_id = ? and current_version = 1", household)).isEqualTo(1);
    }

    @Test
    void nothingChangesWithoutTheConfirmationWord() {
        grant(owner, ownerId, "HOUSEHOLD");
        push(owner, "PRIVATE");
        MvcTestResult refused = reset(owner, "reset please");
        assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(refused).get("code").asString()).isEqualTo("RESET_NOT_CONFIRMED");
        assertThat(count("select count(*) from vault_record where owner_id = ?", ownerId)).isEqualTo(1);
        assertThat(count("select count(*) from household_key_grant where user_id = ?", ownerId)).isEqualTo(1);
    }

    @Test
    void aDependentStartingOverNeverTouchesSharedRecords() {
        grant(owner, ownerId, "HOUSEHOLD");
        UUID shared = push(owner, "SHARED");
        String dependent = join("DEPENDENT");
        jdbc.update("delete from household_key_grant where user_id = ?", ownerId);
        push(dependent, "PRIVATE");

        JsonNode preview = api.body(api.get("/api/v1/keys/me/reset-preview", dependent));
        assertThat(soleHolders(preview)).isZero();
        reset(dependent, "RESET");
        assertThat(count("select count(*) from vault_record where id = ?", shared)).isEqualTo(1);
        assertThat(count("select count(*) from vault_record where owner_id = ?", otherId)).isZero();
    }

    private MvcTestResult reset(String token, String confirm) {
        return api.post("/api/v1/keys/me/reset", token, Map.of("wrappedByPassword", "new-wrapped-by-password",
                "wrappedByRecovery", "new-wrapped-by-recovery", "publicKey", "new-public-key", "encryptedPrivateKey", "new-encrypted-private-key",
                "confirm", confirm));
    }

    private static long soleHolders(JsonNode preview) {
        long n = 0;
        for (JsonNode h : preview.get("households")) {
            if (h.get("soleKeyHolder").asBoolean()) n++;
        }
        return n;
    }

    private long count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Long.class, arg);
    }

    private UUID id(String token) {
        return UUID.fromString(api.body(api.get("/api/v1/me", token)).get("id").asString());
    }

    private MvcTestResult grant(String token, UUID recipient, String kind) {
        return api.post("/api/v1/keys/household/grants", token, List.of(Map.of("userId", recipient.toString(),
                "keyKind", kind, "keyVersion", 1, "wrappedKey", "wrapped-" + kind + "-" + recipient)));
    }

    private String join(String role) {
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", role)));
        api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", other.accessToken(), null));
        other = api.refresh(other.refreshToken());
        return other.accessToken();
    }

    private UUID push(String token, String visibility) {
        String device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        UUID row = UUID.randomUUID();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("visibility", visibility);
        fields.put("name", "PRIVATE".equals(visibility) ? ApiClient.sealedPrivate("Wallet") : ApiClient.sealed("Wallet"));
        Map<String, String> clocks = new HashMap<>();
        fields.keySet().forEach(f -> clocks.put(f, CLOCK));
        MvcTestResult result = api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops",
                List.of(Map.of("opId", UUID.randomUUID().toString(), "table", "cash_account", "rowId", row.toString(), "fields", fields, "clocks", clocks))));
        assertThat(result).hasStatus(HttpStatus.OK);
        return row;
    }
}
