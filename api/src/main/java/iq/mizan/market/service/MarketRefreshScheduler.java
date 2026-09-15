package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Off by default: prices are fetched when a user asks (the Refresh button, or opening the app). */
@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "mizan.market", name = "auto-refresh", havingValue = "true")
public class MarketRefreshScheduler {

    private final MarketService marketService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        marketService.refresh();
    }

    @Scheduled(fixedDelayString = "${mizan.market.refresh-interval}", initialDelayString = "${mizan.market.refresh-interval}")
    public void hourly() {
        marketService.refresh();
    }
}
