package iq.mizan.market.feed;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.entity.Instrument;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** goldapi.io: GET /api/XAU/USD with x-access-token → {"price": 2650.1, "timestamp": 1700000000}. */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "mizan.market.feeds.gold-api", name = "api-key")
public class GoldApiPriceFeed implements PriceFeed {

    private final RestClient client;
    private final MarketProperties.GoldApi config;

    public GoldApiPriceFeed(RestClient.Builder builder, MarketProperties properties) {
        this.config = properties.feeds().goldApi();
        this.client = builder.baseUrl(this.config.url()).defaultHeader("x-access-token", this.config.apiKey()).build();
    }

    @Override
    public String name() {
        return "goldapi.io";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return (instrument == Instrument.XAU || instrument == Instrument.XAG) && Configured.present(config.apiKey());
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get().uri("/api/{symbol}/USD", instrument.name()).retrieve().body(Map.class);
        Object price = body == null ? null : body.get("price");
        if (price == null) {
            return Optional.empty();
        }
        Object timestamp = body.get("timestamp");
        Instant quotedAt = timestamp instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(price), quotedAt));
    }
}
