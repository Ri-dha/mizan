package iq.mizan.market.service;

import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
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
