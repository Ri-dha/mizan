package iq.mizan.household;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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

/** §4.2 P3 and P4: a dependent sees only their own records; an advisor sees reports, never records, and only for a while. */
class RolesIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final String MONTH = "2026-09";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;
    private String ownerDevice;
    private ApiClient.Session otherSession;

    @BeforeEach
    void ownerWithSharedRecords() {
        resetTransactionalData();
        marketService.refresh();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        ownerDevice = device(owner);
        otherSession = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        push(owner, ownerDevice,
                op("cash_account", UUID.randomUUID(), fields("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", 1_000_000)),
                op("income_receipt", UUID.randomUUID(), fields("monthKey", MONTH, "receivedOn", "2026-09-01", "amount", 1_500_000, "currency", "IQD", "fxRateMicros", 1_000_000, "baseAmount", 1_500_000)),
                op("ledger_transaction", UUID.randomUUID(), fields("type", "EXPENSE", "occurredOn", "2026-09-05", "monthKey", MONTH, "amount", 300_000,
                        "currency", "IQD", "fxRateMicros", 1_000_000, "baseAmount", 300_000)),
                op("ledger_transaction", UUID.randomUUID(), fields("visibility", "PRIVATE", "type", "EXPENSE", "occurredOn", "2026-09-06", "monthKey", MONTH,
                        "amount", 100_000, "currency", "IQD", "fxRateMicros", 1_000_000, "baseAmount", 100_000)));
    }

    @Test
    void aDependentSeesOnlyTheirOwnRecordsAndCanStillSync() {
        String dependent = join("DEPENDENT", null);
        String dependentDevice = device(dependent);

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + dependentDevice + "&since=0", dependent));
        assertThat(tables(pull)).doesNotContain("cash_account", "income_receipt", "ledger_transaction", "plan", "bucket");

        UUID goalId = UUID.randomUUID();
        push(dependent, dependentDevice, op("goal", goalId, fields("visibility", "PRIVATE", "name", "Bicycle", "targetAmount", 400_000, "currency", "IQD", "status", "ACTIVE")));
        JsonNode own = api.body(api.get("/api/v1/sync/pull?deviceId=" + dependentDevice + "&since=0", dependent));
        assertThat(tables(own)).contains("goal");

        JsonNode ownerPull = api.body(api.get("/api/v1/sync/pull?deviceId=" + ownerDevice + "&since=0", owner));
        for (JsonNode record : ownerPull.get("records")) {
            assertThat(record.get("rowId").asString()).isNotEqualTo(goalId.toString());
        }
        assertThat(api.get("/api/v1/reports/annual?year=2026", dependent)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAdvisorGetsAggregatesOverSharedRecordsAndNothingElseUntilAccessExpires() {
        String advisor = join("ADVISOR", null);

        JsonNode report = api.body(api.get("/api/v1/reports/annual?year=2026", advisor));
        assertThat(report.get("received").asLong()).isEqualTo(1_500_000);
        assertThat(report.get("spent").asLong()).isEqualTo(300_000);
        assertThat(report.get("savingRate").asDouble()).isEqualTo(80.0);
        JsonNode september = null;
        for (JsonNode month : report.get("months")) {
            if (MONTH.equals(month.get("monthKey").asString())) {
                september = month;
            }
        }
        assertThat(september).isNotNull();
        assertThat(september.get("saved").asLong()).isEqualTo(1_200_000);

        assertThat(api.post("/api/v1/sync/devices", advisor, Map.of("name", "Laptop"))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.get("/api/v1/households/current/members", advisor)).hasStatus(HttpStatus.FORBIDDEN);

        JsonNode members = api.body(api.get("/api/v1/households/current/members", owner));
        JsonNode advisorRow = null;
        for (JsonNode member : members) {
            if ("ADVISOR".equals(member.get("role").asString())) {
                advisorRow = member;
            }
        }
        assertThat(advisorRow).isNotNull();
        assertThat(advisorRow.get("expiresAt").isNull()).isFalse();

        jdbc.update("update membership set expires_at = now() - interval '1 day' where role = 'ADVISOR'");
        assertThat(api.body(api.get("/api/v1/households/current/members", owner))).hasSize(1);
        otherSession = api.refresh(otherSession.refreshToken());
        assertThat(api.body(api.get("/api/v1/me", otherSession.accessToken())).get("household").get("name").asString()).isEqualTo("Noor");
    }

    @Test
    void ownersAndMembersSeeTheSameReportAndBuybackValuationAppliesToNetWorth() {
        JsonNode report = api.body(api.get("/api/v1/reports/annual?year=2026", owner));
        assertThat(report.get("spent").asLong()).isEqualTo(300_000);

        push(owner, ownerDevice,
                op("metal_lot", UUID.randomUUID(), fields("metal", "GOLD", "purityLabel", "21k", "purityBasisPoints", 8750, "weightMg", 5000,
                        "weightUnitEntered", "MITHQAL", "quantityEntered", "1", "purchaseDate", "2026-06-01", "metalCost", 520_000,
                        "makingCharge", 30_000, "fees", 0, "currency", "IQD", "fxRateMicros", 1_000_000, "form", "JEWELLERY")),
                op("market_setting", UUID.randomUUID(), fields("rateKind", "PARALLEL", "goldPremiumBasisPoints", 0, "silverPremiumBasisPoints", 0,
                        "goldMethod", "FIFO", "silverMethod", "FIFO", "valuationBasis", "BUYBACK", "goldBuybackBasisPoints", 300, "silverBuybackBasisPoints", 0)));
        JsonNode figures = api.body(api.get("/api/v1/networth/current", owner));
        long metals = 0;
        for (JsonNode share : figures.get("composition")) {
            if ("METALS".equals(share.get("assetClass").asString())) {
                metals = share.get("amount").asLong();
            }
        }
        assertThat(metals).isEqualTo(531_501);
        assertThat(figures.get("rateSet").get("valuationBasis").asString()).isEqualTo("BUYBACK");
    }

    private String join(String role, Integer accessDays) {
        Map<String, Object> body = accessDays == null ? Map.of("role", role) : Map.of("role", role, "accessDays", accessDays);
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, body));
        api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", otherSession.accessToken(), null));
        otherSession = api.refresh(otherSession.refreshToken());
        return otherSession.accessToken();
    }

    private static List<String> tables(JsonNode pull) {
        List<String> tables = new ArrayList<>();
        pull.get("records").forEach(record -> tables.add(record.get("table").asString()));
        return tables;
    }

    private String device(String token) {
        return api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
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
