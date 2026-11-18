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

/** goldapi.io: GET /api/XAU/USD with x-access-token → {"price": 2650.1, "timestamp": 1700000000}. */
@Component
@Order(20)
public class GoldApiPriceFeed implements PriceFeed {

    private final RestClient client;
    private final MarketProperties.GoldApi config;
    private final MarketSettings settings;

    public GoldApiPriceFeed(RestClient.Builder builder, MarketProperties properties, MarketSettings settings) {
        this.config = properties.feeds().goldApi();
        this.settings = settings;
        this.client = builder.baseUrl(this.config.url()).build();
    }

    @Override
    public String name() {
        return "goldapi.io";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return (instrument == Instrument.XAU || instrument == Instrument.XAG) && Configured.present(settings.goldApiKey());
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get().uri("/api/{symbol}/USD", instrument.name()).header("x-access-token", settings.goldApiKey()).retrieve().body(Map.class);
        Object price = body == null ? null : body.get("price");
        if (price == null) {
            return Optional.empty();
        }
        Object timestamp = body.get("timestamp");
        Instant quotedAt = timestamp instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(price), quotedAt));
    }
}
