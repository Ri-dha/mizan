package iq.mizan.market.feed;

import java.time.Clock;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
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

    private final MarketProperties properties;
    private final Clock clock;

    public ConfiguredParallelRateFeed(MarketProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "configured";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.USDIQD_PARALLEL && properties.feeds().parallelRateIqdMicros() > 0;
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        return Optional.of(new FetchedPrice(properties.feeds().parallelRateIqdMicros(), clock.instant()));
    }
}
