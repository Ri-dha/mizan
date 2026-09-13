package iq.mizan.market.feed;

import java.time.Clock;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.entity.Instrument;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Last in line: real providers answer first wherever they are configured. */
@Component
@Order(1000)
@ConditionalOnProperty(prefix = "mizan.market.stub", name = "enabled", havingValue = "true")
public class StubPriceFeed implements PriceFeed {

    private final MarketProperties properties;
    private final Clock clock;

    public StubPriceFeed(MarketProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return properties.stub().pricesMicros().containsKey(instrument.name());
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        return Optional.ofNullable(properties.stub().pricesMicros().get(instrument.name()))
                .map(price -> new FetchedPrice(price, clock.instant()));
    }
}
