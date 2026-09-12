package iq.mizan.sync;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

class SyncIntegrationTest extends PostgresIntegrationTest {

    private static final String TABLE = "cash_account";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    private String owner;
    private String phone;
    private String tablet;

    @BeforeEach
    void reset() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        phone = registerDevice(owner, "Phone");
        tablet = registerDevice(owner, "Tablet");
    }

    @Test
    void pushedRecordsComeBackOnPullWithTheirClocksAndOwner() {
        UUID rowId = UUID.randomUUID();
        push(owner, phone, op(rowId, Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", 250000),
                clock(1)));

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + tablet + "&since=0", owner));

        List<JsonNode> accounts = cashAccounts(pull);
        assertThat(accounts).hasSize(1);
        JsonNode record = accounts.getFirst();
        assertThat(record.get("table").asString()).isEqualTo(TABLE);
        assertThat(record.get("rowId").asString()).isEqualTo(rowId.toString());
        assertThat(record.get("fields").get("balance").asLong()).isEqualTo(250000);
        assertThat(record.get("fields").get("visibility").asString()).isEqualTo("SHARED");
        assertThat(record.get("clocks").get("name").asString()).isEqualTo(clock(1));
        assertThat(pull.get("nextSeq").asLong()).isPositive();
        assertThat(pull.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void perFieldLastWriteWinsAndTheLoserIsLogged() {
        UUID rowId = UUID.randomUUID();
        push(owner, phone, op(rowId, Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD"), clock(5)));

        JsonNode tabletPush = push(owner, tablet, new SyncOpBuilder(rowId)
                .field("name", "Purse", clock(3))
                .field("balance", 99000, clock(6))
                .build());

        assertThat(tabletPush.get("conflicts")).hasSize(1);
        JsonNode conflict = tabletPush.get("conflicts").get(0);
        assertThat(conflict.get("field").asString()).isEqualTo("name");
        assertThat(conflict.get("clientValue").asString()).isEqualTo("Purse");
        assertThat(conflict.get("serverValue").asString()).isEqualTo("Wallet");

        JsonNode fields = pullSingle(owner, phone).get("fields");
        assertThat(fields.get("name").asString()).isEqualTo("Wallet");
        assertThat(fields.get("balance").asLong()).isEqualTo(99000);

        JsonNode logged = api.body(api.get("/api/v1/sync/conflicts?deviceId=" + tablet, owner));
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).get("rowId").asString()).isEqualTo(rowId.toString());
    }

    @Test
    void replayedOpsAreAcknowledgedWithoutBeingAppliedTwice() {
        UUID rowId = UUID.randomUUID();
        Map<String, Object> op = op(rowId, Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", 1000), clock(1));

        push(owner, phone, op);
        push(owner, tablet, new SyncOpBuilder(rowId).field("balance", 2000, clock(2)).build());
        JsonNode replay = push(owner, phone, op);

        assertThat(replay.get("applied")).hasSize(1);
        assertThat(pullSingle(owner, phone).get("fields").get("balance").asLong()).isEqualTo(2000);
    }

    @Test
    void pullPagesThroughTheFeedInOrder() {
        long afterSeeding = api.body(api.get("/api/v1/sync/pull?deviceId=" + tablet + "&since=0", owner)).get("nextSeq").asLong();
        for (int i = 0; i < 3; i++) {
            push(owner, phone, op(UUID.randomUUID(), Map.of("name", "Account " + i, "kind", "BANK", "currency", "IQD"), clock(i)));
        }

        JsonNode first = api.body(api.get("/api/v1/sync/pull?deviceId=" + tablet + "&since=" + afterSeeding + "&limit=2", owner));
        assertThat(first.get("records")).hasSize(2);
        assertThat(first.get("hasMore").asBoolean()).isTrue();

        JsonNode second = api.body(api.get(
                "/api/v1/sync/pull?deviceId=" + tablet + "&since=" + first.get("nextSeq").asLong() + "&limit=2", owner));
        assertThat(second.get("records")).hasSize(1);
        assertThat(second.get("hasMore").asBoolean()).isFalse();
    }

    @Test
    void anotherHouseholdSeesNothingAndCannotTouchTheRow() {
        UUID rowId = UUID.randomUUID();
        push(owner, phone, op(rowId, Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD"), clock(1)));

        String other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Neighbour").accessToken();
        String otherDevice = registerDevice(other, "Other phone");

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + otherDevice + "&since=0", other));
        assertThat(cashAccounts(pull)).isEmpty();

        MvcTestResult hijack = api.post("/api/v1/sync/push", other, Map.of("deviceId", otherDevice,
                "ops", List.of(new SyncOpBuilder(rowId).field("name", "Mine now", clock(9)).build())));
        assertThat(hijack).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(pullSingle(owner, tablet).get("fields").get("name").asString()).isEqualTo("Wallet");
        assertThat(api.get("/api/v1/sync/pull?deviceId=" + phone + "&since=0", other)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownTablesAndFieldsAreRefused() {
        MvcTestResult unknownTable = api.post("/api/v1/sync/push", owner, Map.of("deviceId", phone, "ops", List.of(Map.of(
                "opId", UUID.randomUUID(), "table", "ledger", "rowId", UUID.randomUUID(),
                "fields", Map.of("x", 1), "clocks", Map.of("x", clock(1))))));
        assertThat(unknownTable).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(unknownTable).get("code").asString()).isEqualTo("SYNC_TABLE_UNKNOWN");

        MvcTestResult unknownField = api.post("/api/v1/sync/push", owner, Map.of("deviceId", phone, "ops",
                List.of(new SyncOpBuilder(UUID.randomUUID()).field("colour", "red", clock(1)).build())));
        assertThat(unknownField).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(unknownField).get("code").asString()).isEqualTo("SYNC_FIELD_UNKNOWN");
    }

    @Test
    void moneyMustBeWholeMinorUnits() {
        MvcTestResult result = api.post("/api/v1/sync/push", owner, Map.of("deviceId", phone, "ops",
                List.of(op(UUID.randomUUID(), Map.of("name", "W", "kind", "WALLET", "currency", "IQD", "balance", 10.5), clock(1)))));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.body(result).get("code").asString()).isEqualTo("VALIDATION_FAILED");
    }

    private String registerDevice(String token, String name) {
        return api.body(api.post("/api/v1/sync/devices", token, Map.of("name", name))).get("id").asString();
    }

    private JsonNode push(String token, String deviceId, Map<String, Object> op) {
        MvcTestResult result = api.post("/api/v1/sync/push", token, Map.of("deviceId", deviceId, "ops", List.of(op)));
        assertThat(result).hasStatus(HttpStatus.OK);
        return api.body(result);
    }

    private JsonNode pullSingle(String token, String deviceId) {
        List<JsonNode> accounts = cashAccounts(api.body(api.get("/api/v1/sync/pull?deviceId=" + deviceId + "&since=0", token)));
        assertThat(accounts).hasSize(1);
        return accounts.getFirst();
    }

    /** The feed also carries the seeded default plan; these tests are about cash accounts only. */
    private static List<JsonNode> cashAccounts(JsonNode pull) {
        List<JsonNode> accounts = new java.util.ArrayList<>();
        for (JsonNode record : pull.get("records")) {
            if (TABLE.equals(record.get("table").asString())) accounts.add(record);
        }
        return accounts;
    }

    private static Map<String, Object> op(UUID rowId, Map<String, Object> fields, String clock) {
        SyncOpBuilder builder = new SyncOpBuilder(rowId);
        fields.forEach((field, value) -> builder.field(field, value, clock));
        return builder.build();
    }

    private static String clock(int tick) {
        return "%013d:%04x:test".formatted(1_700_000_000_000L + tick, 0);
    }

    private static final class SyncOpBuilder {
        private final UUID rowId;
        private final Map<String, Object> fields = new java.util.LinkedHashMap<>();
        private final Map<String, String> clocks = new java.util.LinkedHashMap<>();

        SyncOpBuilder(UUID rowId) {
            this.rowId = rowId;
        }

        SyncOpBuilder field(String name, Object value, String clock) {
            fields.put(name, value);
            clocks.put(name, clock);
            return this;
        }

        Map<String, Object> build() {
            return Map.of("opId", UUID.randomUUID(), "table", TABLE, "rowId", rowId, "fields", fields, "clocks", clocks);
        }
    }
}
