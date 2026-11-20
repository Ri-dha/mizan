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
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** §4.2 P3 and P4: a dependent sees only their own records; an advisor sees shared annual reports, never records, and only for a while. */
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
        assertThat(tables(pull)).doesNotContain("cash_account", "income_receipt", "ledger_transaction");

        UUID goalId = UUID.randomUUID();
        push(dependent, dependentDevice, op("goal", goalId, fields("visibility", "PRIVATE", "name", "Bicycle", "targetAmount", 400_000, "currency", "IQD", "status", "ACTIVE")));
        JsonNode own = api.body(api.get("/api/v1/sync/pull?deviceId=" + dependentDevice + "&since=0", dependent));
        assertThat(tables(own)).contains("goal");

        JsonNode ownerPull = api.body(api.get("/api/v1/sync/pull?deviceId=" + ownerDevice + "&since=0", owner));
        for (JsonNode record : ownerPull.get("records")) {
            assertThat(record.get("rowId").asString()).isNotEqualTo(goalId.toString());
        }
        MvcTestResult sharedWrite = api.post("/api/v1/sync/push", dependent, Map.of("deviceId", dependentDevice,
                "ops", List.of(op("goal", UUID.randomUUID(), fields("name", "Family car", "targetAmount", 9_000_000)))));
        assertThat(sharedWrite).hasStatus(HttpStatus.OK);
        assertThat(api.body(sharedWrite).get("rejected").get(0).get("code").asString()).isEqualTo("SYNC_SHARED_WRITE_REFUSED");
    }

    @Test
    void anAdvisorPullsOnlySharedAnnualReportsAndNothingElseUntilAccessExpires() {
        UUID sharedReport = UUID.randomUUID();
        UUID privateReport = UUID.randomUUID();
        push(owner, ownerDevice,
                op("annual_report", sharedReport, fields("year", 2026, "report", "{\"received\":1500000}", "computedAt", "2026-10-01T00:00:00Z")),
                op("annual_report", privateReport, fields("visibility", "PRIVATE", "year", 2026, "report", "{}", "computedAt", "2026-10-01T00:00:00Z")));
        String advisor = join("ADVISOR", null);
        String advisorDevice = device(advisor);

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + advisorDevice + "&since=0", advisor));
        List<String> rows = new ArrayList<>();
        pull.get("records").forEach(record -> rows.add(record.get("rowId").asString()));
        assertThat(tables(pull)).containsOnly("annual_report");
        assertThat(rows).containsExactly(sharedReport.toString());

        assertThat(api.post("/api/v1/sync/push", advisor, Map.of("deviceId", advisorDevice, "ops",
                List.of(op("annual_report", UUID.randomUUID(), fields("year", 2025)))))).hasStatus(HttpStatus.FORBIDDEN);
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
        boolean isPrivate = false;
        for (int i = 0; i < keysAndValues.length; i += 2) {
            isPrivate |= "visibility".equals(keysAndValues[i]) && "PRIVATE".equals(keysAndValues[i + 1]);
        }
        for (int i = 0; i < keysAndValues.length; i += 2) {
            String field = (String) keysAndValues[i];
            Object value = keysAndValues[i + 1];
            boolean plain = "visibility".equals(field) || "deletedAt".equals(field);
            map.put(field, plain ? value : isPrivate ? ApiClient.sealedPrivate(value) : ApiClient.sealed(value));
        }
        return map;
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        Map<String, String> clocks = new java.util.HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, CLOCK));
        return Map.of("opId", UUID.randomUUID().toString(), "table", table, "rowId", rowId.toString(), "fields", fields, "clocks", clocks);
    }
}
