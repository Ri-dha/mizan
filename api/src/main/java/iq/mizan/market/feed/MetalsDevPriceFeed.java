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

/** metals.dev: {"metals": {"gold": 2650.1, "silver": 31.2}} in USD per troy ounce. */
@Component
@Order(10)
@ConditionalOnProperty(prefix = "mizan.market.feeds.metals-dev", name = "api-key")
public class MetalsDevPriceFeed implements PriceFeed {

    private static final Map<Instrument, String> METALS = Map.of(Instrument.XAU, "gold", Instrument.XAG, "silver");

    private final RestClient client;
    private final MarketProperties.MetalsDev config;

    public MetalsDevPriceFeed(RestClient.Builder builder, MarketProperties properties) {
        this.config = properties.feeds().metalsDev();
        this.client = builder.baseUrl(config.url()).build();
    }

    @Override
    public String name() {
        return "metals.dev";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return METALS.containsKey(instrument);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<String, Object> body = client.get()
                .uri(uri -> uri.queryParam("api_key", config.apiKey()).queryParam("currency", "USD").queryParam("unit", "toz").build())
                .retrieve()
                .body(Map.class);
        Map<String, Object> metals = (Map<String, Object>) body.get("metals");
        Object price = metals == null ? null : metals.get(METALS.get(instrument));
        return price == null ? Optional.empty() : Optional.of(new FetchedPrice(Micros.of(price), Instant.now()));
    }
}
