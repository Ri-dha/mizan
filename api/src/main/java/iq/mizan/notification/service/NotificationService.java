package iq.mizan.notification.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.notification.NotificationProperties;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository subscriptions;
    private final NotificationLogRepository sentLog;
    private final NotificationRules rules;
    private final NotificationMessages messages;
    private final PushSender sender;
    private final NotificationProperties properties;
    private final TenantSession tenantSession;
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

    /**
     * One member's reminders for today, evaluated through their own row-level view. Runs in its
     * own transaction so one member's failure never holds up the rest of the household.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deliverDue(UUID userId, UUID householdId) {
        tenantSession.bind(userId, householdId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(properties.timeZone())));
        List<NotificationRules.Finding> findings = rules.evaluate(householdId, localeOf(userId), settingsOf(userId, householdId), today);
        if (findings.isEmpty()) {
            return 0;
        }
        List<PushSubscription> devices = subscriptions.findByUserIdAndHouseholdId(userId, householdId);
        int sent = 0;
        for (NotificationRules.Finding finding : findings) {
            String kind = finding.message().kind();
            if (sentLog.existsByUserIdAndKindAndDedupeKey(userId, kind, finding.dedupeKey())) {
                continue;
            }
            sentLog.save(NotificationLog.sent(userId, kind, finding.dedupeKey(), clock.instant()));
            for (PushSubscription device : devices) {
                if (deliver(device, finding.message()) == PushSender.Outcome.SENT) {
                    sent++;
                }
            }
        }
        return sent;
    }

    private PushSender.Outcome deliver(PushSubscription subscription, PushMessage message) {
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

    private NotificationSettings settingsOf(UUID userId, UUID householdId) {
        return jdbc.sql("select bill_due, bill_lead_days, pay_day, overspend, overspend_threshold_bp, month_close, metal_price, metal_move_bp, quiet_mode "
                        + "from notification_setting where owner_id = ? and household_id = ? and deleted_at is null limit 1")
                .param(userId).param(householdId).query().listOfRows().stream().findFirst()
                .map(row -> new NotificationSettings((Boolean) row.get("bill_due"), ((Number) row.get("bill_lead_days")).intValue(),
                        (Boolean) row.get("pay_day"), (Boolean) row.get("overspend"), ((Number) row.get("overspend_threshold_bp")).intValue(),
                        (Boolean) row.get("month_close"), (Boolean) row.get("metal_price"), ((Number) row.get("metal_move_bp")).intValue(),
                        (Boolean) row.get("quiet_mode")))
                .orElse(NotificationSettings.defaults(properties.defaults()));
    }

    private String localeOf(UUID userId) {
        return jdbc.sql("select locale from app_user where id = ?").param(userId).query(String.class).optional().orElse("en");
    }

    private SubscriptionResponse toResponse(PushSubscription subscription) {
        return new SubscriptionResponse(subscription.getId(), subscription.getEndpoint(), subscription.getUserAgent(), subscription.getCreatedAt());
    }

    Map<String, Object> describe() {
        return Map.of("transport", properties.transport(), "timeZone", properties.timeZone());
    }
}
