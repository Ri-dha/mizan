package iq.mizan.market.service;

import java.util.EnumSet;
import java.util.Set;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.IqWealthFxFeed;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spreads the free IQWealth quota over the day: often while the Baghdad market trades, a few times
 * overnight, once at start-up. Each poll stores the dollar rates for everyone.
 */
@Component
@AllArgsConstructor
public class IqWealthPoller {

    private static final Logger log = LoggerFactory.getLogger(IqWealthPoller.class);
    private static final Set<Instrument> DOLLAR = EnumSet.of(Instrument.USDIQD_PARALLEL, Instrument.USDIQD_OFFICIAL);

    private final IqWealthFxFeed feed;
    private final MarketService marketService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!feed.hasRates()) {
            pollAndStore();
        }
    }

    @Scheduled(cron = "${mizan.market.feeds.iq-wealth.day-cron}", zone = "${mizan.market.feeds.iq-wealth.zone}")
    public void duringTrading() {
        pollAndStore();
    }

    @Scheduled(cron = "${mizan.market.feeds.iq-wealth.night-cron}", zone = "${mizan.market.feeds.iq-wealth.zone}")
    public void overnight() {
        pollAndStore();
    }

    public void pollAndStore() {
        try {
            if (feed.poll() == IqWealthFxFeed.Outcome.UPDATED) {
                marketService.refresh(DOLLAR);
            }
        } catch (RuntimeException e) {
            log.warn("IQWealth poll failed: {}", e.getMessage());
        }
    }
}
