package iq.mizan.notification.job;

import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.repository.PushSubscriptionRepository;
import iq.mizan.notification.repository.Recipient;
import iq.mizan.notification.service.NotificationService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a day, every member with a subscribed device gets what the rules found for them. */
@Component
@AllArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final PushSubscriptionRepository subscriptions;
    private final NotificationService notificationService;
    private final NotificationProperties properties;

    @Scheduled(cron = "${mizan.notifications.daily-cron}", zone = "${mizan.notifications.time-zone}")
    public void daily() {
        if (!properties.enabled()) {
            return;
        }
        run();
    }

    public int run() {
        int sent = 0;
        for (Recipient recipient : subscriptions.recipients()) {
            try {
                sent += notificationService.deliverDue(recipient.userId(), recipient.householdId());
            } catch (RuntimeException e) {
                log.warn("Notifications for user {} failed: {}", recipient.userId(), e.getMessage());
            }
        }
        if (sent > 0) {
            log.info("Sent {} notifications", sent);
        }
        return sent;
    }
}
