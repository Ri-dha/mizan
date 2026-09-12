package iq.mizan.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class DefaultPlanSeedingTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @BeforeEach
    void reset() {
        resetTransactionalData();
    }

    @Test
    void aNewHouseholdPullsTheDefaultPlanAndItsBuckets() {
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        String device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + device + "&since=0", token));

        JsonNode plan = only(pull, "plan");
        assertThat(plan.get("fields").get("effectiveFrom").asString()).matches("\\d{4}-\\d{2}");
        assertThat(plan.get("clocks").get("effectiveFrom").asString()).endsWith(":server");

        int totalShares = 0;
        int buckets = 0;
        for (JsonNode record : pull.get("records")) {
            if (!"bucket".equals(record.get("table").asString())) continue;
            buckets++;
            totalShares += record.get("fields").get("shareBasisPoints").asInt();
            assertThat(record.get("fields").get("planId").asString()).isEqualTo(plan.get("rowId").asString());
        }
        assertThat(buckets).isEqualTo(5);
        assertThat(totalShares).isEqualTo(10000);
    }

    @Test
    void anotherHouseholdGetsItsOwnPlanNotTheNeighbours() {
        api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");
        String other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Neighbour").accessToken();
        String device = api.body(api.post("/api/v1/sync/devices", other, Map.of("name", "Phone"))).get("id").asString();

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + device + "&since=0", other));

        long plans = 0;
        for (JsonNode record : pull.get("records")) {
            if ("plan".equals(record.get("table").asString())) plans++;
        }
        assertThat(plans).isEqualTo(1);
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
