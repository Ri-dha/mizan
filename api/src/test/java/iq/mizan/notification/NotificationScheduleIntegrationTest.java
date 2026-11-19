package iq.mizan.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import iq.mizan.notification.entity.PushChannel;
import iq.mizan.notification.push.LoggingPushSender;
import iq.mizan.notification.service.NotificationService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** ADR e2e-1: devices hand the server only a kind and a time; due reminders go out once, stale ones never. */
class NotificationScheduleIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LoggingPushSender pushes;

    @Test
    void dueRemindersAreSentOnceAndStaleOnesAreDropped() {
        resetTransactionalData();
        String owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        assertThat(api.post("/api/v1/notifications/subscriptions", owner,
                Map.of("endpoint", "https://push.example.test/device", "p256dh", "p256dh-key", "auth", "auth-key"))).hasStatus(HttpStatus.CREATED);
        Instant now = Instant.now();
        Map<String, Object> schedule = Map.of("metalPriceAlerts", false, "items", List.of(
                Map.of("kind", "BILL_DUE", "fireAt", now.minus(Duration.ofMinutes(1)).toString(), "dedupeKey", "bill:1:2026-11-18"),
                Map.of("kind", "PAY_DAY", "fireAt", now.minus(Duration.ofDays(1)).toString(), "dedupeKey", "pay:1:2026-11-17"),
                Map.of("kind", "MONTH_CLOSE", "fireAt", now.plus(Duration.ofDays(3)).toString(), "dedupeKey", "close:2026-11")));

        assertThat(api.body(api.put("/api/v1/notifications/schedule", owner, schedule)).get("scheduled").asInt()).isEqualTo(3);
        assertThat(notificationService.dispatchDue()).isEqualTo(1);
        assertThat(notificationService.dispatchDue()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification_schedule where sent_at is null", Long.class)).isEqualTo(1);

        assertThat(api.put("/api/v1/notifications/schedule", owner, Map.of("metalPriceAlerts", false, "items",
                List.of(Map.of("kind", "BILL_DUE", "fireAt", now.toString(), "dedupeKey", "Rent 500000 IQD"))))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aPhoneTokenReceivesRemindersAndIsListedOnceHoweverOftenItRegisters() {
        resetTransactionalData();
        pushes.clear();
        String owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        Map<String, Object> phone = Map.of("token", "ExponentPushToken[abc123_XYZ-9]");
        assertThat(api.post("/api/v1/notifications/phones", owner, phone)).hasStatus(HttpStatus.CREATED);
        assertThat(api.post("/api/v1/notifications/phones", owner, phone)).hasStatus(HttpStatus.CREATED);
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where channel = 'EXPO'", Long.class)).isEqualTo(1);
        assertThat(api.post("/api/v1/notifications/phones", owner, Map.of("token", "not-a-token"))).hasStatus(HttpStatus.BAD_REQUEST);

        api.put("/api/v1/notifications/schedule", owner, Map.of("metalPriceAlerts", false, "items",
                List.of(Map.of("kind", "BILL_DUE", "fireAt", Instant.now().minus(Duration.ofMinutes(1)).toString(), "dedupeKey", "bill:phone"))));
        assertThat(notificationService.dispatchDue()).isEqualTo(1);
        assertThat(pushes.deliveries()).singleElement().satisfies(delivery -> {
            assertThat(delivery.subscription().getChannel()).isEqualTo(PushChannel.EXPO);
            assertThat(delivery.message().url()).isEqualTo("/bills");
        });

        assertThat(api.delete("/api/v1/notifications/phones", owner, phone)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from push_subscription", Long.class)).isZero();
    }
}
