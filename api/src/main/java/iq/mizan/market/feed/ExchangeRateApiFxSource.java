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

/** open.er-api.com: keyless, {"rates": {"IQD": 1310.0}, "time_last_update_unix": ...}. Official rate only. */
@Component
@Order(30)
@ConditionalOnProperty(prefix = "mizan.market.feeds.exchange-rate-api", name = "url")
public class ExchangeRateApiFxSource implements PriceFeed {

    private final RestClient client;
    private final String url;

    public ExchangeRateApiFxSource(RestClient.Builder builder, MarketProperties properties) {
        this.url = properties.feeds().exchangeRateApi().url();
        this.client = builder.baseUrl(this.url).build();
    }

    @Override
    public String name() {
        return "open.er-api.com";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.USDIQD_OFFICIAL && Configured.present(url);
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get().uri("/v6/latest/USD").retrieve().body(Map.class);
        Map<?, ?> rates = body == null ? null : (Map<?, ?>) body.get("rates");
        Object rate = rates == null ? null : rates.get("IQD");
        if (rate == null) {
            return Optional.empty();
        }
        Object updated = body.get("time_last_update_unix");
        Instant quotedAt = updated instanceof Number seconds ? Instant.ofEpochSecond(seconds.longValue()) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(rate), quotedAt));
    }
}
