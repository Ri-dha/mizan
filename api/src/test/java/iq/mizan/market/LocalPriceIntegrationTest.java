package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.market.service.MarketService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** Phase 4: choosing the local market source values gold at the dealer's quote per gram of pure metal. */
class LocalPriceIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Test
    void theLocalQuoteReplacesSpotTimesRateWhenChosen() {
        resetTransactionalData();
        marketService.refresh();
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        String device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        UUID settingId = UUID.randomUUID();
        push(token, device,
                op("metal_lot", UUID.randomUUID(), fields("metal", "GOLD", "purityLabel", "21k", "purityBasisPoints", 8750, "weightMg", 5000,
                        "weightUnitEntered", "MITHQAL", "quantityEntered", "1", "purchaseDate", "2026-06-01", "metalCost", 520_000,
                        "makingCharge", 30_000, "fees", 0, "currency", "IQD", "fxRateMicros", 1_000_000, "form", "JEWELLERY")),
                op("market_setting", settingId, fields("rateKind", "PARALLEL", "goldPremiumBasisPoints", 0, "silverPremiumBasisPoints", 0,
                        "goldMethod", "FIFO", "silverMethod", "FIFO", "valuationBasis", "MARKET", "goldBuybackBasisPoints", 0, "silverBuybackBasisPoints", 0,
                        "priceSource", "LOCAL")));

        JsonNode figures = api.body(api.get("/api/v1/networth/current", token));
        assertThat(metals(figures)).isEqualTo(547_938);
        assertThat(figures.get("rateSet").get("priceSource").asString()).isEqualTo("LOCAL");
        assertThat(figures.get("rateSet").get("xauLocalSource").asString()).startsWith("stub@");

        JsonNode quotes = api.body(api.get("/api/v1/market/quotes", token));
        boolean sawLocal = false;
        for (JsonNode quote : quotes) {
            sawLocal |= "XAU_LOCAL".equals(quote.get("instrument").asString());
        }
        assertThat(sawLocal).isTrue();
    }

    private static long metals(JsonNode figures) {
        for (JsonNode share : figures.get("composition")) {
            if ("METALS".equals(share.get("assetClass").asString())) {
                return share.get("amount").asLong();
            }
        }
        throw new AssertionError("no metals share");
    }

    @SafeVarargs
    private void push(String token, String deviceId, Map<String, Object>... ops) {
        assertThat(api.post("/api/v1/sync/push", token, Map.of("deviceId", deviceId, "ops", List.of(ops)))).hasStatus(HttpStatus.OK);
    }

    private static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        Map<String, String> clocks = new java.util.HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, CLOCK));
        return Map.of("opId", UUID.randomUUID().toString(), "table", table, "rowId", rowId.toString(), "fields", fields, "clocks", clocks);
    }
}
