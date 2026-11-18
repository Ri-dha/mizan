package iq.mizan.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;
import iq.mizan.sync.job.SyncHousekeeping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** ADR sync-2: the sync tables keep what the protocol needs and no more, and a late retry still lands once. */
class SyncHousekeepingTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SyncHousekeeping housekeeping;

    private String owner;
    private String device;

    @BeforeEach
    void ownerWithADevice() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        device = api.body(api.post("/api/v1/sync/devices", owner, Map.of("name", "Phone"))).get("id").asString();
    }

    @Test
    void onlyTheNewestLogEntryPerRecordIsKeptAndAFreshDeviceStillGetsEverything() {
        UUID wallet = UUID.randomUUID();
        UUID bank = UUID.randomUUID();
        push(op(wallet, "Wallet", "1700000000001:0000:dev1"));
        push(op(wallet, "Wallet 2", "1700000000002:0000:dev1"));
        push(op(wallet, "Wallet 3", "1700000000003:0000:dev1"));
        push(op(bank, "Bank", "1700000000004:0000:dev1"));
        assertThat(logEntries(wallet)).isEqualTo(3);

        assertThat(housekeeping.run().logEntries()).isEqualTo(2);

        assertThat(logEntries(wallet)).isEqualTo(1);
        assertThat(logEntries(bank)).isEqualTo(1);
        String fresh = api.body(api.post("/api/v1/sync/devices", owner, Map.of("name", "Laptop"))).get("id").asString();
        JsonNode pulled = api.body(api.get("/api/v1/sync/pull?deviceId=" + fresh + "&since=0", owner)).get("records");
        assertThat(pulled.valueStream().map(r -> r.get("rowId").asString()).toList()).containsExactlyInAnyOrder(wallet.toString(), bank.toString());
    }

    @Test
    void oldReceiptsGoAndARetryAfterThatChangesNothingAndRaisesNoConflict() {
        UUID wallet = UUID.randomUUID();
        Map<String, Object> first = op(wallet, "Wallet", "1700000000001:0000:dev1");
        push(first);
        String stored = jdbc.queryForObject("select fields::text from vault_record where id = ?", String.class, wallet);
        jdbc.update("update sync_op_receipt set applied_at = now() - interval '31 days'");

        assertThat(housekeeping.run().receipts()).isEqualTo(1);

        Map<String, Object> resealed = new LinkedHashMap<>(first);
        resealed.put("fields", Map.of("visibility", "SHARED", "name", ApiClient.sealed("Wallet, sealed again")));
        JsonNode result = api.body(push(resealed));
        assertThat(result.get("conflicts")).isEmpty();
        assertThat(jdbc.queryForObject("select fields::text from vault_record where id = ?", String.class, wallet)).isEqualTo(stored);
    }

    @Test
    void syncedRecordsAreNotAuditedOneByOne() {
        push(op(UUID.randomUUID(), "Wallet", "1700000000001:0000:dev1"));

        assertThat(jdbc.queryForObject("select count(*) from audit_entry where action in ('SYNC_INSERTED', 'SYNC_UPDATED')", Integer.class)).isZero();
    }

    private Map<String, Object> op(UUID row, String name, String clock) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("visibility", "SHARED");
        fields.put("name", ApiClient.sealed(name));
        Map<String, String> clocks = new HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, clock));
        return Map.of("opId", UUID.randomUUID().toString(), "table", "cash_account", "rowId", row.toString(), "fields", fields, "clocks", clocks);
    }

    private MvcTestResult push(Map<String, Object> op) {
        MvcTestResult result = api.post("/api/v1/sync/push", owner, Map.of("deviceId", device, "ops", List.of(op)));
        assertThat(result).hasStatus(HttpStatus.OK);
        return result;
    }

    private int logEntries(UUID row) {
        return jdbc.queryForObject("select count(*) from sync_log where row_id = ?", Integer.class, row);
    }
}
