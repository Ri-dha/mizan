package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import iq.mizan.common.jobs.JobLock;
import java.time.Duration;

/** Off by default: prices are fetched when a user asks (the Refresh button, or opening the app). */
@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "mizan.market", name = "auto-refresh", havingValue = "true")
public class MarketRefreshScheduler {

    private final MarketService marketService;
    private final JobLock jobLock;

    private static final Duration POLL_AT_MOST = Duration.ofMinutes(10);
    private static final Duration POLL_AT_LEAST = Duration.ofMinutes(1);

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        jobLock.runExclusively("market-refresh", POLL_AT_MOST, POLL_AT_LEAST, marketService::refresh);
    }

    @Scheduled(fixedDelayString = "${mizan.market.refresh-interval}", initialDelayString = "${mizan.market.refresh-interval}")
    public void hourly() {
        jobLock.runExclusively("market-refresh", POLL_AT_MOST, POLL_AT_LEAST, marketService::refresh);
    }
}
