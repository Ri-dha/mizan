package iq.mizan.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** ADR sync-4: a device's wait answers at once when behind, wakes on another device's push, and times out quietly. */
class SyncWaitIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MockMvcTester mvc;

    private String owner;
    private String phone;
    private String tablet;

    @BeforeEach
    void reset() {
        resetTransactionalData();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        phone = device("Phone");
        tablet = device("Tablet");
    }

    @Test
    void aDeviceBehindTheFeedIsToldAtOnce() {
        push(phone);
        assertThat(waitFor(0).get("changed").asBoolean()).isTrue();
    }

    @Test
    void aWaitingDeviceWakesWhenAnotherDevicePushes() throws Exception {
        long since = latestSeq();
        long started = System.nanoTime();
        CompletableFuture<JsonNode> waiting = CompletableFuture.supplyAsync(() -> waitFor(since));
        Thread.sleep(200);
        push(tablet);

        JsonNode answer = waiting.get(5, TimeUnit.SECONDS);
        assertThat(answer.get("changed").asBoolean()).isTrue();
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1000);
    }

    @Test
    void aWaitWithNothingNewEndsUnchanged() {
        assertThat(waitFor(latestSeq()).get("changed").asBoolean()).isFalse();
    }

    @Test
    void waitingNeedsASignIn() {
        assertThat(mvc.get().uri("/api/v1/sync/wait?since=0").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private JsonNode waitFor(long since) {
        MvcTestResult result = mvc.get().uri("/api/v1/sync/wait?since=" + since).header("Authorization", "Bearer " + owner).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        return api.body(result);
    }

    private long latestSeq() {
        return api.body(api.get("/api/v1/sync/pull?deviceId=" + phone + "&since=0", owner)).get("nextSeq").asLong();
    }

    private String device(String name) {
        return api.body(api.post("/api/v1/sync/devices", owner, Map.of("name", name))).get("id").asString();
    }

    private void push(String deviceId) {
        UUID rowId = UUID.randomUUID();
        String clock = "%013d:%04x:test".formatted(System.currentTimeMillis(), 0);
        JsonNode body = api.body(api.post("/api/v1/sync/push", owner, Map.of("deviceId", deviceId, "ops", List.of(Map.of(
                "opId", UUID.randomUUID(), "table", "cash_account", "rowId", rowId,
                "fields", Map.of("visibility", "SHARED", "name", ApiClient.sealed("Wallet")),
                "clocks", Map.of("visibility", clock, "name", clock))))));
        assertThat(body.get("rejected")).isEmpty();
    }
}
