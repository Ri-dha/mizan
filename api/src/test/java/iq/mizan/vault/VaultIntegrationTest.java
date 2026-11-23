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

/** ADR e2e-1: the server stores ciphertext, hands out wrapped keys by role, and drops plain copies once migrated. */
class VaultIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final String LATER_CLOCK = "1700000000001:0000:test";
    private static final long BALANCE = 1_250_000;

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;
    private UUID ownerId;
    private String ownerDevice;
    private ApiClient.Session otherSession;
    private UUID otherId;

    @BeforeEach
    void twoAccounts() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        ownerId = UUID.fromString(api.body(api.get("/api/v1/me", owner)).get("id").asString());
        ownerDevice = device(owner);
        otherSession = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        otherId = UUID.fromString(api.body(api.get("/api/v1/me", otherSession.accessToken())).get("id").asString());
    }

    @Test
    void noAmountOrNameReachesTheDatabaseInTheClear() {
        push(owner, ownerDevice, op("cash_account", UUID.randomUUID(), sealedFields("name", "Wallet", "currency", "IQD", "balance", BALANCE)));

        String stored = jdbc.queryForObject("select fields::text from vault_record where table_name = 'cash_account'", String.class);
        assertThat(stored).doesNotContain("Wallet").doesNotContain(String.valueOf(BALANCE));
    }

    @Test
    void aSnapshotCannotBeRewrittenOnceStored() {
        UUID snapshot = UUID.randomUUID();
        push(owner, ownerDevice, op("net_worth_snapshot", snapshot, sealedFields("totalAssets", BALANCE)));
        String stored = jdbc.queryForObject("select fields::text from vault_record where id = ?", String.class, snapshot);

        MvcTestResult rewrite = api.post("/api/v1/sync/push", owner, Map.of("deviceId", ownerDevice,
                "ops", List.of(op("net_worth_snapshot", snapshot, sealedFields("totalAssets", 1), LATER_CLOCK))));
        assertThat(rewrite).hasStatus(HttpStatus.OK);
        assertThat(api.body(rewrite).get("rejected").get(0).get("code").asString()).isEqualTo("SYNC_RECORD_IMMUTABLE");

        // ADR sync-2: the same write sent again (identical clocks) is a retry: acknowledged, nothing changes.
        MvcTestResult retry = api.post("/api/v1/sync/push", owner, Map.of("deviceId", ownerDevice,
                "ops", List.of(op("net_worth_snapshot", snapshot, sealedFields("totalAssets", 1)))));
        assertThat(retry).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select fields::text from vault_record where id = ?", String.class, snapshot)).isEqualTo(stored);
    }

    @Test
    void theOwnerStartsTheHouseholdKeyAndMembersPassOnlyWhatTheyHold() {
        assertThat(grant(owner, ownerId, "HOUSEHOLD", 2)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(grant(owner, ownerId, "HOUSEHOLD", 1)).hasStatus(HttpStatus.OK);
        assertThat(grant(owner, ownerId, "HOUSEHOLD", 1)).hasStatus(HttpStatus.OK);

        String member = join("MEMBER");
        assertThat(grant(member, otherId, "REPORT", 1)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(grant(owner, otherId, "HOUSEHOLD", 1)).hasStatus(HttpStatus.OK);

        JsonNode mine = api.body(api.get("/api/v1/keys/me", member));
        assertThat(mine.get("account").get("publicKey").asString()).isEqualTo("public-key-" + accounts.otherEmail());
        assertThat(mine.get("grants")).hasSize(1);
        assertThat(mine.get("grants").get(0).get("wrappedKey").asString()).isEqualTo("wrapped-HOUSEHOLD-" + otherId);
    }

    @Test
    void aDependentMayNotHoldTheHouseholdKeyAndAnAdvisorOnlyTheReportKey() {
        grant(owner, ownerId, "HOUSEHOLD", 1);
        grant(owner, ownerId, "REPORT", 1);
        join("DEPENDENT");
        assertThat(grant(owner, otherId, "HOUSEHOLD", 1)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(grant(owner, otherId, "REPORT", 1)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aPasswordChangeRewrapsTheUserKeyWithoutTouchingTheOthers() {
        MvcTestResult rewrap = api.patch("/api/v1/keys/me", owner, Map.of("wrappedByPassword", "rewrapped"));
        assertThat(rewrap).hasStatus(HttpStatus.OK);
        JsonNode account = api.body(api.get("/api/v1/keys/me", owner)).get("account");
        assertThat(account.get("wrappedByPassword").asString()).isEqualTo("rewrapped");
        assertThat(account.get("wrappedByRecovery").asString()).isEqualTo("wrapped-by-recovery-" + accounts.ownerEmail());
        assertThat(api.put("/api/v1/keys/me", owner, ApiClient.accountKeys("again"))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void withNoPlainTablesLeftCompletingTheMigrationOnlyMarksTheHousehold() {
        JsonNode before = api.body(api.get("/api/v1/vault/status", owner));
        assertThat(before.get("legacyRows").asLong()).isZero();

        JsonNode after = api.body(api.post("/api/v1/vault/migration/complete", owner, null));
        assertThat(after.get("legacyRows").asLong()).isZero();
        assertThat(after.get("migrated").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select to_regclass('public.cash_account') is null", Boolean.class)).isTrue();
    }

    private MvcTestResult grant(String token, UUID recipient, String kind, int version) {
        return api.post("/api/v1/keys/household/grants", token, List.of(Map.of("userId", recipient.toString(),
                "keyKind", kind, "keyVersion", version, "wrappedKey", "wrapped-" + kind + "-" + recipient)));
    }

    private String join(String role) {
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", role)));
        api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", otherSession.accessToken(), null));
        otherSession = api.refresh(otherSession.refreshToken());
        return otherSession.accessToken();
    }

    private String device(String token) {
        return api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
    }

    @SafeVarargs
    private void push(String token, String deviceId, Map<String, Object>... ops) {
        MvcTestResult result = api.post("/api/v1/sync/push", token, Map.of("deviceId", deviceId, "ops", List.of(ops)));
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private static Map<String, Object> sealedFields(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], ApiClient.sealed(keysAndValues[i + 1]));
        }
        return map;
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        return op(table, rowId, fields, CLOCK);
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields, String clock) {
        Map<String, String> clocks = new HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, clock));
        return Map.of("opId", UUID.randomUUID().toString(), "table", table, "rowId", rowId.toString(), "fields", fields, "clocks", clocks);
    }
}
