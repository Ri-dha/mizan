package iq.mizan.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.market.service.MarketService;
import iq.mizan.notification.job.NotificationScheduler;
import iq.mizan.notification.push.LoggingPushSender;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** FR-NTF-01..06: the daily job finds today's reminders through each member's own view and sends each once. */
class NotificationRulesIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final String ENDPOINT = "https://push.example.test/send/phone-1";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Autowired
    private NotificationScheduler scheduler;

    @Autowired
    private LoggingPushSender pushes;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private String device;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void subscribeAndSeed() {
        resetTransactionalData();
        pushes.clear();
        marketService.refresh();
        token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        device = api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
        assertThat(api.post("/api/v1/notifications/subscriptions", token, Map.of("endpoint", ENDPOINT, "p256dh", "BPk", "auth", "YQ")))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void billsDueSoonPayDayAndOverspendingAreFoundOnceEach() {
        String monthKey = today.toString().substring(0, 7);
        UUID bucketId = UUID.fromString(jdbc.queryForObject("select id from bucket order by sort_order limit 1", String.class));
        push(
                op("recurring_expense", UUID.randomUUID(), fields("name", "Rent", "amount", 600_000, "currency", "IQD", "fxRateMicros", 1_000_000,
                        "frequency", "CUSTOM", "anchorDate", today.plusDays(2).toString(), "intervalDays", 30, "activeFrom", today.minusDays(60).toString(), "isEstimate", false)),
                op("recurring_expense", UUID.randomUUID(), fields("name", "Far away", "amount", 100_000, "currency", "IQD", "fxRateMicros", 1_000_000,
                        "frequency", "CUSTOM", "anchorDate", today.plusDays(20).toString(), "intervalDays", 30, "activeFrom", today.minusDays(60).toString(), "isEstimate", false)),
                op("income_source", UUID.randomUUID(), fields("name", "Salary", "amount", 1_500_000, "currency", "IQD", "fxRateMicros", 1_000_000,
                        "frequency", "MONTHLY", "payDay", today.getDayOfMonth(), "activeFrom", today.minusMonths(6).toString())),
                op("income_receipt", UUID.randomUUID(), fields("monthKey", monthKey, "receivedOn", today.toString(), "amount", 1_000_000,
                        "currency", "IQD", "fxRateMicros", 1_000_000, "baseAmount", 1_000_000)),
                op("ledger_transaction", UUID.randomUUID(), fields("type", "EXPENSE", "occurredOn", today.toString(), "monthKey", monthKey,
                        "amount", 540_000, "currency", "IQD", "fxRateMicros", 1_000_000, "baseAmount", 540_000, "bucketId", bucketId.toString())));

        int sent = scheduler.run();

        List<String> kinds = pushes.deliveries().stream().map(d -> d.message().kind()).toList();
        assertThat(kinds).containsExactlyInAnyOrder("BILL_DUE", "PAY_DAY", "OVERSPEND");
        assertThat(sent).isEqualTo(3);
        assertThat(pushes.deliveries().stream().filter(d -> "BILL_DUE".equals(d.message().kind())).findFirst().orElseThrow().message().body())
                .contains("Rent").contains("2 days");
        assertThat(pushes.deliveries().stream().filter(d -> "OVERSPEND".equals(d.message().kind())).findFirst().orElseThrow().message().body())
                .contains("98%");

        pushes.clear();
        assertThat(scheduler.run()).isZero();
        assertThat(pushes.deliveries()).isEmpty();
    }

    @Test
    void quietModeAndSwitchesSilenceTheJobAndArabicUsersGetArabic() {
        push(op("recurring_expense", UUID.randomUUID(), fields("name", "Rent", "amount", 600_000, "currency", "IQD", "fxRateMicros", 1_000_000,
                "frequency", "CUSTOM", "anchorDate", today.toString(), "intervalDays", 30, "activeFrom", today.minusDays(60).toString(), "isEstimate", false)));
        push(op("notification_setting", UUID.randomUUID(), fields("visibility", "PRIVATE", "billDue", true, "billLeadDays", 3, "payDay", true,
                "overspend", true, "overspendThresholdBp", 9000, "monthClose", true, "metalPrice", false, "metalMoveBp", 200, "quietMode", true)));
        assertThat(scheduler.run()).isZero();

        jdbc.update("update notification_setting set quiet_mode = false, bill_due = false");
        assertThat(scheduler.run()).isZero();

        jdbc.update("update notification_setting set bill_due = true");
        jdbc.update("update app_user set locale = 'ar'");
        assertThat(scheduler.run()).isEqualTo(1);
        assertThat(pushes.deliveries().getFirst().message().title()).isEqualTo("فاتورة مستحقة");
        assertThat(pushes.deliveries().getFirst().message().url()).isEqualTo("/bills");
    }

    @Test
    void aPerBillReminderOverridesTheDefaultLeadTime() {
        push(op("recurring_expense", UUID.randomUUID(), fields("name", "Generator", "amount", 50_000, "currency", "IQD", "fxRateMicros", 1_000_000,
                "frequency", "CUSTOM", "anchorDate", today.plusDays(7).toString(), "intervalDays", 30, "activeFrom", today.minusDays(60).toString(),
                "isEstimate", false, "reminderDays", 7)));
        assertThat(scheduler.run()).isEqualTo(1);
        assertThat(pushes.deliveries().getFirst().message().body()).contains("Generator").contains("7 days");
    }

    @Test
    void aTestMessageReachesEveryDeviceAndUnsubscribingStopsIt() {
        JsonNode result = api.body(api.post("/api/v1/notifications/test", token, null));
        assertThat(result.get("sent").asInt()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/notifications/subscriptions", token))).hasSize(1);
        assertThat(api.body(api.get("/api/v1/notifications/vapid-key", token)).get("publicKey").asString()).isEmpty();

        assertThat(api.delete("/api/v1/notifications/subscriptions", token, Map.of("endpoint", ENDPOINT))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.body(api.post("/api/v1/notifications/test", token, null)).get("sent").asInt()).isZero();
    }

    @SafeVarargs
    private void push(Map<String, Object>... ops) {
        assertThat(api.post("/api/v1/sync/push", token, Map.of("deviceId", device, "ops", List.of(ops)))).hasStatus(HttpStatus.OK);
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
