package iq.mizan.notification.job;

import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.service.NotificationService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends the reminders devices scheduled as they fall due, and once a day checks public price moves. */
@Component
@AllArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;

    @Scheduled(fixedDelayString = "${mizan.notifications.dispatch-interval}")
    public void dispatch() {
        if (!properties.enabled()) {
            return;
        }
        int sent = notificationService.dispatchDue();
        if (sent > 0) {
            log.info("Sent {} scheduled notifications", sent);
        }
    }

    @Scheduled(cron = "${mizan.notifications.metal-cron}", zone = "${mizan.notifications.time-zone}")
    public void metalMoves() {
        if (properties.enabled()) {
            notificationService.alertMetalMoves();
        }
    }
}
