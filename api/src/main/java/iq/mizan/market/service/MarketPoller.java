package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    @Scheduled(cron = "${mizan.market.poll-cron}", zone = "${mizan.market.poll-zone}")
    public void poll() {
        try {
            marketService.refresh();
        } catch (RuntimeException e) {
            log.warn("Scheduled market poll failed: {}", e.getMessage());
        }
    }
}
