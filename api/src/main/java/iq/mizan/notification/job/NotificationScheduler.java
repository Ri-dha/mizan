package iq.mizan.notification.job;

import iq.mizan.notification.NotificationProperties;
import iq.mizan.notification.service.NotificationService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import iq.mizan.common.jobs.JobLock;
import java.time.Duration;

/** Sends the reminders devices scheduled as they fall due, and once a day checks public price moves. */
@Component
@AllArgsConstructor
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final JobLock jobLock;

    /** Two instances must never send the same due reminder; the dispatch itself marks what it sent. */
    private static final Duration DISPATCH_AT_MOST = Duration.ofMinutes(4);
    private static final Duration DISPATCH_AT_LEAST = Duration.ofSeconds(30);
    private static final Duration ALERTS_AT_MOST = Duration.ofMinutes(30);
    private static final Duration ALERTS_AT_LEAST = Duration.ofMinutes(5);

    @Scheduled(fixedDelayString = "${mizan.notifications.dispatch-interval}")
    public void dispatch() {
        if (!properties.enabled()) {
            return;
        }
        jobLock.runExclusively("notification-dispatch", DISPATCH_AT_MOST, DISPATCH_AT_LEAST, () -> {
            int sent = notificationService.dispatchDue();
            if (sent > 0) {
                log.info("Sent {} scheduled notifications", sent);
            }
        });
    }

    @Scheduled(cron = "${mizan.notifications.metal-cron}", zone = "${mizan.notifications.time-zone}")
    public void metalMoves() {
        if (properties.enabled()) {
            jobLock.runExclusively("metal-move-alerts", ALERTS_AT_MOST, ALERTS_AT_LEAST, notificationService::alertMetalMoves);
        }
    }
}
