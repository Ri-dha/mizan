package iq.mizan.market.feed;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * gold-api.com: keyless spot in USD per troy ounce,
 * {"price": 4289.1, "symbol": "XAU", "updatedAt": "2026-09-14T23:46:39Z"}. Keyed providers
 * sit ahead of it in the order; it is what a fresh install gets live prices from.
 */
@Component
@Order(20)
public class GoldApiComPriceFeed implements PriceFeed {

    private final RestClient client;
    private final MarketSettings settings;

    public GoldApiComPriceFeed(RestClient.Builder builder, MarketProperties properties, MarketSettings settings) {
        this.client = builder.baseUrl(properties.feeds().goldApiCom().url()).build();
        this.settings = settings;
    }

    @Override
    public String name() {
        return "gold-api.com";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return settings.goldApiComEnabled() && (instrument == Instrument.XAU || instrument == Instrument.XAG);
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get().uri("/price/{symbol}", instrument.name()).retrieve().body(Map.class);
        return parse(body);
    }

    static Optional<FetchedPrice> parse(Map<?, ?> body) {
        Object price = body == null ? null : body.get("price");
        if (price == null) {
            return Optional.empty();
        }
        Object updated = body.get("updatedAt");
        Instant quotedAt = updated instanceof String text ? Instant.parse(text) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(price), quotedAt));
    }
}
