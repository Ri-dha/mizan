package iq.mizan.market.service;

import java.time.Duration;

import iq.mizan.common.jobs.JobLock;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Also on start-up, so a fresh install has the year of history at once rather than after the first run. */
@Component
@AllArgsConstructor
@ConditionalOnExpression("'${mizan.market.world-rates.cron}' != '-'")
public class WorldRatePoller {

    private static final Logger log = LoggerFactory.getLogger(WorldRatePoller.class);
    private static final Duration AT_MOST = Duration.ofMinutes(10);
    private static final Duration AT_LEAST = Duration.ofMinutes(1);

    private final WorldRateService worldRates;
    private final JobLock jobLock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        poll();
    }

    @Scheduled(cron = "${mizan.market.world-rates.cron}", zone = "${mizan.market.world-rates.zone}")
    public void poll() {
        try {
            jobLock.runExclusively("world-rates", AT_MOST, AT_LEAST, worldRates::poll);
        } catch (RuntimeException e) {
            log.warn("World rates fetch failed: {}", e.getMessage());
        }
    }
}
