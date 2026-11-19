package iq.mizan.notification.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.dto.RegisterPhoneRequest;
import iq.mizan.notification.dto.ScheduleRequest;
import iq.mizan.notification.dto.SubscribeRequest;
import iq.mizan.notification.dto.SubscriptionResponse;
import iq.mizan.notification.entity.NotificationLog;
import iq.mizan.notification.entity.PushSubscription;
import iq.mizan.notification.push.PushMessage;
import iq.mizan.notification.push.PushSender;
import iq.mizan.notification.repository.NotificationLogRepository;
import iq.mizan.notification.repository.PushSubscriptionRepository;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery only (ADR e2e-1): devices upload when a reminder is due and of what kind; this service
 * sends fixed wording at that time. The one rule still evaluated here, metal price moves, runs on
 * public prices and knows nothing about anyone's holdings.
 */
@Service
@AllArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_FAILURES = 5;
    private static final int DISPATCH_BATCH = 500;
    private static final int DEFAULT_METAL_MOVE_BP = 200;
    private static final double BASIS_POINTS_PER_PERCENT = 100.0;
    private static final Map<String, List<String>> METAL_INSTRUMENTS = Map.of(
            "GOLD", List.of("XAU_LOCAL", "XAU"), "SILVER", List.of("XAG_LOCAL", "XAG"));

    private final PushSubscriptionRepository subscriptions;
    private final NotificationLogRepository sentLog;
    private final NotificationMessages messages;
    private final List<PushSender> senders;
    private final NotificationProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Transactional
    public SubscriptionResponse subscribe(CurrentUser user, SubscribeRequest request, String userAgent) {
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .map(existing -> {
                    existing.rebind(user.userId(), user.householdId(), request.p256dh(), request.auth());
                    return existing;
                })
                .orElseGet(() -> subscriptions.save(PushSubscription.register(
                        user.userId(), user.householdId(), request.endpoint(), request.p256dh(), request.auth(), userAgent)));
        return toResponse(subscription);
    }

    /** ADR push-1: a phone's token takes the place of a browser endpoint; the same phone is never listed twice. */
    @Transactional
    public SubscriptionResponse registerPhone(CurrentUser user, RegisterPhoneRequest request, String userAgent) {
        PushSubscription subscription = subscriptions.findByEndpoint(request.token())
                .map(existing -> {
                    existing.rebindPhone(user.userId(), user.householdId());
                    return existing;
                })
                .orElseGet(() -> subscriptions.save(PushSubscription.registerPhone(user.userId(), user.householdId(), request.token(), userAgent)));
        return toResponse(subscription);
    }

    @Transactional
    public void unsubscribe(CurrentUser user, String endpoint) {
        subscriptions.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUserId().equals(user.userId()))
                .ifPresent(subscriptions::delete);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mine(CurrentUser user) {
        return subscriptions.findByUserIdAndHouseholdId(user.userId(), user.householdId()).stream().map(this::toResponse).toList();
    }

    /** Sends a test message to every device of the caller so they can see the channel works. */
    @Transactional
    public int sendTest(CurrentUser user) {
        String locale = localeOf(user.userId());
        int sent = 0;
        for (PushSubscription subscription : subscriptions.findByUserIdAndHouseholdId(user.userId(), user.householdId())) {
            if (deliver(subscription, messages.test(locale)) == PushSender.Outcome.SENT) {
                sent++;
            }
        }
        return sent;
    }

    /** Unsent reminders are replaced; sent ones keep their row so the same key is never sent twice. */
    @Transactional
    public int replaceSchedule(CurrentUser user, ScheduleRequest request) {
        jdbc.sql("delete from notification_schedule where user_id = ? and household_id = ? and sent_at is null")
                .param(user.userId()).param(user.householdId()).update();
        int stored = 0;
        for (ScheduleRequest.Item item : request.items()) {
            stored += jdbc.sql("insert into notification_schedule (id, user_id, household_id, kind, fire_at, dedupe_key) values (?, ?, ?, ?, ?, ?) "
                            + "on conflict (user_id, household_id, kind, dedupe_key) do nothing")
                    .param(UUID.randomUUID()).param(user.userId()).param(user.householdId()).param(item.kind().name())
                    .param(Timestamp.from(item.fireAt())).param(item.dedupeKey())
                    .update();
        }
        int moveBp = request.metalMoveBasisPoints() == null ? DEFAULT_METAL_MOVE_BP : request.metalMoveBasisPoints();
        jdbc.sql("insert into notification_preference (user_id, metal_price, metal_move_bp) values (?, ?, ?) "
                        + "on conflict (user_id) do update set metal_price = excluded.metal_price, metal_move_bp = excluded.metal_move_bp")
                .param(user.userId()).param(request.metalPriceAlerts()).param(moveBp).update();
        return stored;
    }

    /** Due reminders go out once; ones older than the stale window are dropped rather than sent late. */
    @Transactional
    public int dispatchDue() {
        Instant now = clock.instant();
        List<Map<String, Object>> due = jdbc.sql("select id, user_id, household_id, kind, fire_at from notification_schedule "
                        + "where sent_at is null and fire_at <= ? order by fire_at limit ?")
                .param(Timestamp.from(now)).param(DISPATCH_BATCH).query().listOfRows();
        int sent = 0;
        for (Map<String, Object> row : due) {
            UUID userId = (UUID) row.get("user_id");
            UUID householdId = (UUID) row.get("household_id");
            boolean stale = ((Timestamp) row.get("fire_at")).toInstant().isBefore(now.minus(properties.staleAfter()));
            if (!stale) {
                PushMessage message = messages.reminder(localeOf(userId), ReminderKind.valueOf((String) row.get("kind")));
                for (PushSubscription device : subscriptions.findByUserIdAndHouseholdId(userId, householdId)) {
                    if (deliver(device, message) == PushSender.Outcome.SENT) {
                        sent++;
                    }
                }
            }
            jdbc.sql("update notification_schedule set sent_at = ? where id = ?").param(Timestamp.from(now)).param(row.get("id")).update();
        }
        return sent;
    }

    /** Once a day: a move in the public price beyond the member's threshold, at most once per metal per day. */
    @Transactional
    public int alertMetalMoves() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(properties.timeZone())));
        List<Map<String, Object>> watchers = jdbc.sql("select p.user_id, p.metal_move_bp, s.household_id from notification_preference p "
                        + "join push_subscription s on s.user_id = p.user_id where p.metal_price group by p.user_id, p.metal_move_bp, s.household_id")
                .query().listOfRows();
        int sent = 0;
        for (Map<String, Object> watcher : watchers) {
            UUID userId = (UUID) watcher.get("user_id");
            int threshold = ((Number) watcher.get("metal_move_bp")).intValue();
            for (var metal : METAL_INSTRUMENTS.entrySet()) {
                Double move = dailyMovePercent(metal.getValue(), today);
                String dedupe = metal.getKey() + ":" + today;
                if (move == null || Math.abs(move) * BASIS_POINTS_PER_PERCENT < threshold
                        || sentLog.existsByUserIdAndKindAndDedupeKey(userId, "METAL_PRICE", dedupe)) {
                    continue;
                }
                sentLog.save(NotificationLog.sent(userId, "METAL_PRICE", dedupe, clock.instant()));
                PushMessage message = messages.metalMove(localeOf(userId), metal.getKey(), move);
                for (PushSubscription device : subscriptions.findByUserIdAndHouseholdId(userId, (UUID) watcher.get("household_id"))) {
                    if (deliver(device, message) == PushSender.Outcome.SENT) {
                        sent++;
                    }
                }
            }
        }
        return sent;
    }

    private Double dailyMovePercent(List<String> instruments, LocalDate today) {
        for (String instrument : instruments) {
            List<Long> prices = jdbc.sql("select price_micros from price_history where instrument = ? and day in (?, ?) order by day")
                    .param(instrument).param(today.minusDays(1)).param(today).query(Long.class).list();
            if (prices.size() == 2 && prices.getFirst() > 0) {
                return (prices.get(1) - prices.getFirst()) * BASIS_POINTS_PER_PERCENT / prices.getFirst();
            }
        }
        return null;
    }

    private PushSender.Outcome deliver(PushSubscription subscription, PushMessage message) {
        PushSender sender = senders.stream().filter(candidate -> candidate.handles(subscription.getChannel())).findFirst().orElse(null);
        if (sender == null) {
            log.warn("No transport for {} push subscriptions", subscription.getChannel());
            return PushSender.Outcome.FAILED;
        }
        PushSender.Outcome outcome = sender.send(subscription, message);
        switch (outcome) {
            case SENT -> subscription.recordSuccess();
            case GONE -> subscriptions.delete(subscription);
            case FAILED -> {
                subscription.recordFailure();
                if (subscription.getFailures() >= MAX_FAILURES) {
                    log.info("Dropping push subscription {} after {} failures", subscription.getId(), subscription.getFailures());
                    subscriptions.delete(subscription);
                }
            }
        }
        return outcome;
    }

    private String localeOf(UUID userId) {
        return jdbc.sql("select locale from app_user where id = ?").param(userId).query(String.class).optional().orElse("en");
    }

    private SubscriptionResponse toResponse(PushSubscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getEndpoint(), subscription.getUserAgent(), subscription.getCreatedAt());
    }
}
