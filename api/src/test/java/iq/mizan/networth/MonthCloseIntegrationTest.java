package iq.mizan.networth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.market.service.MarketService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class MonthCloseIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private String device;

    @BeforeEach
    void seedALedger() {
        resetTransactionalData();
        marketService.refresh();
        token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        UUID debtId = UUID.randomUUID();
        push(
                op("cash_account", UUID.randomUUID(), Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", 1_250_000)),
                op("metal_lot", UUID.randomUUID(), fields("metal", "GOLD", "purityLabel", "21k", "purityBasisPoints", 8750, "weightMg", 5000,
                        "weightUnitEntered", "MITHQAL", "quantityEntered", "1", "purchaseDate", "2026-06-01", "metalCost", 520_000,
                        "makingCharge", 30_000, "fees", 0, "currency", "IQD", "fxRateMicros", 1_000_000, "form", "JEWELLERY")),
                op("debt", debtId, fields("name", "Car loan", "direction", "OWING", "principal", 5_000_000, "currency", "IQD",
                        "fxRateMicros", 1_000_000, "annualRateBasisPoints", 1200, "monthlyPayment", 250_000, "startDate", "2026-09-01", "status", "ACTIVE")),
                op("debt_payment", UUID.randomUUID(), Map.of("debtId", debtId.toString(), "paidOn", "2026-09-13", "monthKey", "2026-09",
                        "amount", 250_000, "interestComponent", 50_000, "principalComponent", 200_000)),
                op("debt", UUID.randomUUID(), fields("name", "Lent to Ali", "direction", "OWED", "principal", 300_000, "currency", "IQD",
                        "fxRateMicros", 1_000_000, "annualRateBasisPoints", 0, "monthlyPayment", 0, "startDate", "2026-08-01", "status", "ACTIVE")));
    }

    @Test
    void netWorthReconcilesToItsComponents() {
        JsonNode figures = api.body(api.get("/api/v1/networth/current", token));

        long cash = 1_250_000;
        long gold = 547_939;
        long receivables = 300_000;
        long liabilities = 4_800_000;
        assertThat(figures.get("totalAssets").asLong()).isEqualTo(cash + gold + receivables);
        assertThat(figures.get("totalLiabilities").asLong()).isEqualTo(liabilities);
        assertThat(figures.get("netWorth").asLong()).isEqualTo(cash + gold + receivables - liabilities);
        assertThat(figures.get("rateSet").get("usdIqdMicros").asLong()).isEqualTo(1_470_000_000L);
        assertThat(figures.get("rateSet").get("xauSource").asString()).isEqualTo("local");
        assertThat(figures.get("rateSet").get("xauLocalSource").asString()).startsWith("stub@");
    }

    @Test
    void closingTakesASnapshotThatDevicesPullButCannotPushOrChange() {
        JsonNode closed = api.body(api.post("/api/v1/months/2026-09/close", token, null));
        assertThat(closed.get("closed").asBoolean()).isTrue();
        assertThat(api.post("/api/v1/months/2026-09/close", token, null)).hasStatus(HttpStatus.CONFLICT);

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + device + "&since=0", token));
        JsonNode snapshot = only(pull, "net_worth_snapshot");
        assertThat(snapshot.get("fields").get("netWorth").asLong()).isEqualTo(1_250_000 + 547_939 + 300_000 - 4_800_000);
        assertThat(snapshot.get("fields").get("rateSet").get("rateKind").asString()).isEqualTo("PARALLEL");
        assertThat(snapshot.get("clocks").get("netWorth").asString()).endsWith(":server");

        var forged = api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops",
                List.of(op("net_worth_snapshot", UUID.fromString(snapshot.get("rowId").asString()), Map.of("netWorth", 1)))));
        assertThat(forged).hasStatus(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> jdbc.update("update net_worth_snapshot set net_worth = 1"))
                .hasMessageContaining("immutable");
    }

    @Test
    void reopeningIsExplicitAndClosingAgainTakesAFreshSnapshot() {
        api.post("/api/v1/months/2026-09/close", token, null);
        assertThat(api.post("/api/v1/months/2026-08/reopen", token, null)).hasStatus(HttpStatus.CONFLICT);

        JsonNode reopened = api.body(api.post("/api/v1/months/2026-09/reopen", token, null));
        assertThat(reopened.get("closed").asBoolean()).isFalse();
        assertThat(reopened.get("reopenedAt").isNull()).isFalse();

        api.body(api.post("/api/v1/months/2026-09/close", token, null));
        JsonNode months = api.body(api.get("/api/v1/months", token));
        assertThat(months).hasSize(1);
        assertThat(months.get(0).get("closed").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from net_worth_snapshot", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where action in ('MONTH_CLOSED','MONTH_REOPENED')", Long.class)).isEqualTo(3);
    }

    private void push(Map<String, Object>... ops) {
        var result = api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops", List.of(ops)));
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    /** Map.of stops at ten pairs; ledger rows need more. */
    private static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        Map<String, String> clocks = new java.util.HashMap<>();
        fields.keySet().forEach(f -> clocks.put(f, CLOCK));
        return Map.of("opId", UUID.randomUUID(), "table", table, "rowId", rowId, "fields", fields, "clocks", clocks);
    }

    private static JsonNode only(JsonNode pull, String table) {
        JsonNode found = null;
        for (JsonNode record : pull.get("records")) {
            if (table.equals(record.get("table").asString())) {
                assertThat(found).isNull();
                found = record;
            }
        }
        assertThat(found).isNotNull();
        return found;
    }
}
