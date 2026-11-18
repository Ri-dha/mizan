package iq.mizan.market.feed;

import java.time.Clock;
import java.util.Optional;

import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The parallel-market dollar has no public API (BRD §9), so an operator enters it through
 * configuration. Zero means "not set" and lets the stub or a user override stand in.
 */
@Component
@Order(100)
public class ConfiguredParallelRateFeed implements PriceFeed {

    private final MarketSettings settings;
    private final Clock clock;

    public ConfiguredParallelRateFeed(MarketSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "configured";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.USDIQD_PARALLEL && settings.manualDollarMicros() > 0;
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        return Optional.of(new FetchedPrice(settings.manualDollarMicros(), clock.instant()));
    }
}
