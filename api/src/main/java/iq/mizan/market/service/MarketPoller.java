package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import iq.mizan.common.jobs.JobLock;
import java.time.Duration;

/**
 * T-price-history: gold and silver are fetched on a schedule during Baghdad trading hours, so a
 * price change is recorded even when nobody opens the app. Keyed spot providers count each poll
 * against their quota; with one configured, set the cron to fit it.
 */
@Component
@AllArgsConstructor
public class MarketPoller {

    private static final Logger log = LoggerFactory.getLogger(MarketPoller.class);

    private final MarketService marketService;
    private final JobLock jobLock;

    private static final Duration POLL_AT_MOST = Duration.ofMinutes(10);
    private static final Duration POLL_AT_LEAST = Duration.ofMinutes(1);

    @Scheduled(cron = "${mizan.market.poll-cron}", zone = "${mizan.market.poll-zone}")
    public void poll() {
        try {
            jobLock.runExclusively("market-poll", POLL_AT_MOST, POLL_AT_LEAST, marketService::refresh);
        } catch (RuntimeException e) {
            log.warn("Scheduled market poll failed: {}", e.getMessage());
        }
    }
}
