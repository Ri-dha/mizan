package iq.mizan.market.feed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.MarketCatalogue;
import iq.mizan.market.repository.MarketCatalogueRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Baghdad Bullion House: {"as_of", "stale", "base": {"gold_iqd_g": {"bid", "ask"}, "silver_iqd_g": {...}},
 * "groups": [...]} in IQD per gram of pure metal. Ask is what you pay and feeds XAU_LOCAL; bid is what
 * the dealer pays and feeds the buy-back instruments. One download serves all four instruments and
 * the product catalogue.
 */
@Component
@Order(3)
@ConditionalOnProperty(prefix = "mizan.market.feeds.baghdad-bullion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BaghdadBullionHouseFeed implements PriceFeed {

    public static final String NAME = "baghdadbullionhouse.com";
    private static final Duration BODY_TTL = Duration.ofSeconds(30);

    private final RestClient client;
    private final MarketProperties.BaghdadBullion config;
    private final MarketCatalogueRepository catalogues;
    private final JsonMapper json;
    private final Clock clock;
    private Map<?, ?> cachedBody;
    private Instant cachedAt = Instant.EPOCH;

    public BaghdadBullionHouseFeed(RestClient.Builder builder, MarketProperties properties, MarketCatalogueRepository catalogues,
                                   JsonMapper json, Clock clock) {
        this.config = properties.feeds().baghdadBullion();
        this.client = builder.build();
        this.catalogues = catalogues;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return Configured.present(config.url()) && switch (instrument) {
            case XAU_LOCAL, XAU_LOCAL_BID, XAG_LOCAL, XAG_LOCAL_BID -> true;
            default -> false;
        };
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = body();
        if (body == null || Boolean.TRUE.equals(body.get("stale"))) {
            return Optional.empty();
        }
        return parse(body, instrument);
    }

    static Optional<FetchedPrice> parse(Map<?, ?> body, Instrument instrument) {
        Object base = body.get("base");
        if (!(base instanceof Map<?, ?> prices)) {
            return Optional.empty();
        }
        String metal = instrument == Instrument.XAU_LOCAL || instrument == Instrument.XAU_LOCAL_BID ? "gold_iqd_g" : "silver_iqd_g";
        String side = instrument == Instrument.XAU_LOCAL_BID || instrument == Instrument.XAG_LOCAL_BID ? "bid" : "ask";
        Object quote = prices.get(metal) instanceof Map<?, ?> pair ? pair.get(side) : null;
        if (quote == null) {
            return Optional.empty();
        }
        Object asOf = body.get("as_of");
        Instant quotedAt = asOf instanceof String text ? Instant.parse(text) : Instant.now();
        return Optional.of(new FetchedPrice(Micros.of(quote), quotedAt));
    }

    /** One request per refresh cycle; the catalogue is saved from the same body. */
    private synchronized Map<?, ?> body() {
        Instant now = clock.instant();
        if (cachedBody != null && cachedAt.plus(BODY_TTL).isAfter(now)) {
            return cachedBody;
        }
        Map<?, ?> body = client.get().uri(config.url()).retrieve().body(Map.class);
        cachedBody = body;
        cachedAt = now;
        if (body != null && body.get("groups") != null && !Boolean.TRUE.equals(body.get("stale"))) {
            Instant asOf = body.get("as_of") instanceof String text ? Instant.parse(text) : now;
            String payload = json.writeValueAsString(body.get("groups"));
            catalogues.findById(NAME).ifPresentOrElse(
                    catalogue -> catalogue.record(now, asOf, payload),
                    () -> catalogues.save(MarketCatalogue.of(NAME, now, asOf, payload)));
        }
        return body;
    }
}
