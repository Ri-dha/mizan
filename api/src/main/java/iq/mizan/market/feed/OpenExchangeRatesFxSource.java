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

/** openexchangerates.org: {"rates": {"IQD": 1310.0}, "timestamp": ...}. Official rate only. */
@Component
@Order(40)
public class OpenExchangeRatesFxSource implements PriceFeed {

    private final RestClient client;
    private final MarketProperties.OpenExchangeRates config;
    private final MarketSettings settings;

    public OpenExchangeRatesFxSource(RestClient.Builder builder, MarketProperties properties, MarketSettings settings) {
        this.config = properties.feeds().openExchangeRates();
        this.settings = settings;
        this.client = builder.baseUrl(config.url()).build();
    }

    @Override
    public String name() {
        return "openexchangerates.org";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.USDIQD_OFFICIAL && Configured.present(settings.openExchangeRatesAppId());
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get()
                .uri(uri -> uri.path("/api/latest.json").queryParam("app_id", settings.openExchangeRatesAppId()).queryParam("symbols", "IQD").build())
                .retrieve()
                .body(Map.class);
        Map<?, ?> rates = body == null ? null : (Map<?, ?>) body.get("rates");
        Object rate = rates == null ? null : rates.get("IQD");
        if (rate == null) {
            return Optional.empty();
        }
        Object timestamp = body.get("timestamp");
        Instant quotedAt = timestamp instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(rate), quotedAt));
    }
}
