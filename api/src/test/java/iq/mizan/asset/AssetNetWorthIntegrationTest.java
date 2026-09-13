package iq.mizan.asset;

import static org.assertj.core.api.Assertions.assertThat;

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
import tools.jackson.databind.JsonNode;

/** FR-AST-01..06, BR-13: assets enter net worth at their depreciated latest valuation and leave it when sold. */
class AssetNetWorthIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final String LATER_CLOCK = "1700000000001:0000:test";
    private static final long CAR_PRICE = 25_000_000;
    private static final long HOUSE_PRICE = 120_000_000;
    private static final long WALLET = 1_000_000;

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    private String token;
    private String device;
    private final UUID carId = UUID.randomUUID();
    private final UUID houseId = UUID.randomUUID();

    @BeforeEach
    void seedAssets() {
        resetTransactionalData();
        marketService.refresh();
        token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        push(
                op("cash_account", UUID.randomUUID(), Map.of("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", WALLET)),
                op("asset", carId, asset("VEHICLE", "Kia Sportage", CAR_PRICE, "LIQUID", "NONE",
                        Map.of("make", "Kia", "model", "Sportage", "year", 2022, "plate", "BGD 12345"))),
                op("asset", houseId, asset("PROPERTY", "Flat in Mansour", HOUSE_PRICE, "ILLIQUID", "NONE", Map.of("areaSqm", 140))));
    }

    @Test
    void assetsCountByClassAndTheIlliquidOnesAreSplitOut() {
        JsonNode figures = api.body(api.get("/api/v1/networth/current", token));

        assertThat(figures.get("totalAssets").asLong()).isEqualTo(WALLET + CAR_PRICE + HOUSE_PRICE);
        assertThat(figures.get("liquidAssets").asLong()).isEqualTo(WALLET + CAR_PRICE);
        assertThat(figures.get("illiquidAssets").asLong()).isEqualTo(HOUSE_PRICE);
        assertThat(amountOf(figures, "VEHICLES")).isEqualTo(CAR_PRICE);
        assertThat(amountOf(figures, "PROPERTY")).isEqualTo(HOUSE_PRICE);
    }

    @Test
    void theLatestValuationReplacesThePurchasePrice() {
        push(op("asset_valuation", UUID.randomUUID(), Map.of("assetId", carId.toString(), "valuedOn", "2026-09-01", "value", 21_000_000, "source", "MANUAL")),
                op("asset_valuation", UUID.randomUUID(), Map.of("assetId", carId.toString(), "valuedOn", "2025-09-01", "value", 23_000_000, "source", "MANUAL")));

        JsonNode figures = api.body(api.get("/api/v1/networth/current", token));
        assertThat(amountOf(figures, "VEHICLES")).isEqualTo(21_000_000);
    }

    @Test
    void aSoldAssetLeavesNetWorthAndASnapshotRecordsTheSplit() {
        push(op("asset", houseId, Map.of("status", "SOLD", "soldOn", "2026-09-10", "salePrice", 130_000_000), LATER_CLOCK));

        JsonNode figures = api.body(api.get("/api/v1/networth/current", token));
        assertThat(figures.get("totalAssets").asLong()).isEqualTo(WALLET + CAR_PRICE);
        assertThat(figures.get("illiquidAssets").asLong()).isZero();

        api.body(api.post("/api/v1/months/2026-09/close", token, null));
        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + device + "&since=0", token));
        JsonNode snapshot = only(pull, "net_worth_snapshot").get("fields");
        assertThat(snapshot.get("liquidAssets").asLong()).isEqualTo(WALLET + CAR_PRICE);
        assertThat(snapshot.get("illiquidAssets").asLong()).isZero();
        assertThat(snapshot.get("composition")).anySatisfy(share ->
                assertThat(share.get("assetClass").asString()).isEqualTo("VEHICLES"));
    }

    private static long amountOf(JsonNode figures, String assetClass) {
        for (JsonNode share : figures.get("composition")) {
            if (assetClass.equals(share.get("assetClass").asString())) {
                return share.get("amount").asLong();
            }
        }
        throw new AssertionError("No share for " + assetClass);
    }

    private static JsonNode only(JsonNode pull, String table) {
        JsonNode found = null;
        for (JsonNode record : pull.get("records")) {
            if (table.equals(record.get("table").asString())) {
                assertThat(found).as("more than one %s row", table).isNull();
                found = record;
            }
        }
        assertThat(found).as("no %s row", table).isNotNull();
        return found;
    }

    private static Map<String, Object> asset(String type, String name, long price, String liquidity, String method, Map<String, Object> attributes) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("type", type);
        fields.put("name", name);
        fields.put("purchaseDate", "2024-03-10");
        fields.put("purchasePrice", price);
        fields.put("currency", "IQD");
        fields.put("fxRateMicros", 1_000_000);
        fields.put("liquidity", liquidity);
        fields.put("depreciationMethod", method);
        fields.put("annualRateBasisPoints", 0);
        fields.put("salvageValue", 0);
        fields.put("attributes", attributes);
        fields.put("status", "HELD");
        return fields;
    }

    @SafeVarargs
    private void push(Map<String, Object>... ops) {
        var result = api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops", List.of(ops)));
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        return op(table, rowId, fields, CLOCK);
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields, String clock) {
        Map<String, String> clocks = new java.util.HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, clock));
        return Map.of("opId", UUID.randomUUID().toString(), "table", table, "rowId", rowId.toString(), "fields", fields, "clocks", clocks);
    }
}
