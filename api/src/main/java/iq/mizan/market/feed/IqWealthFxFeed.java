package iq.mizan.market.feed;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.repository.PriceQuoteRepository;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * IQWealth (iraqsm.com) {@code GET /fx}: the Baghdad market dollar and the Central Bank rate.
 * The network is touched only by {@link #poll()} on a schedule, through the shared
 * {@link IqWealthClient}; {@link #fetch} answers from the last poll, which keeps a user's
 * Refresh from spending the quota.
 */
@Component
@Order(2)
public class IqWealthFxFeed implements PriceFeed {

    public static final String NAME = IqWealthClient.NAME;

    public enum Side { BUY, SELL, MID }

    record Rates(Long parallelMicros, Instant parallelAt, Long officialMicros, Instant officialAt) {
    }

    private final IqWealthClient iqWealth;
    private final MarketSettings settings;
    private final StoredQuotes stored;
    private volatile Rates rates;

    public IqWealthFxFeed(IqWealthClient iqWealth, MarketSettings settings, PriceQuoteRepository quotes) {
        this.iqWealth = iqWealth;
        this.settings = settings;
        this.stored = new StoredQuotes(quotes, iqWealth);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.USDIQD_PARALLEL || instrument == Instrument.USDIQD_OFFICIAL;
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Rates known = rates;
        if (known == null) {
            return stored.last(instrument);
        }
        return instrument == Instrument.USDIQD_PARALLEL
                ? price(known.parallelMicros(), known.parallelAt())
                : price(known.officialMicros(), known.officialAt());
    }

    public boolean hasRates() {
        return rates != null;
    }

    /** One request through the shared budget; the outcome says whether the rates changed. */
    public synchronized IqWealthClient.Outcome poll() {
        IqWealthClient.Reply reply = iqWealth.get("/fx");
        if (!reply.answered()) {
            return iqWealth.recorded(reply.skipped());
        }
        Rates next = parse(reply.body(), Side.valueOf(settings.iqWealthParallelSide()));
        boolean changed = !next.equals(rates);
        rates = next;
        return iqWealth.recorded(changed ? IqWealthClient.Outcome.UPDATED : IqWealthClient.Outcome.UNCHANGED);
    }

    /** For the operator dashboard. */
    public IqWealthClient.Quota quota() {
        return iqWealth.quota();
    }

    /** {"data": {"parallel": {"buy", "sell", "stale", "publishedAt"}, "official": {"cbi"}}, "generatedAt"}; a stale parallel rate is left out. */
    static Rates parse(Map<?, ?> body, Side side) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) {
            throw new IllegalStateException("IQWealth /fx answered without data");
        }
        Instant generatedAt = body.get("generatedAt") instanceof String text ? Instant.parse(text) : Instant.now();
        Long parallel = null;
        Instant parallelAt = null;
        if (data.get("parallel") instanceof Map<?, ?> market && !Boolean.TRUE.equals(market.get("stale"))) {
            parallel = sideOf(market, side);
            parallelAt = market.get("publishedAt") instanceof String text ? Instant.parse(text) : generatedAt;
        }
        Long official = data.get("official") instanceof Map<?, ?> cbi && cbi.get("cbi") != null ? Micros.of(cbi.get("cbi")) : null;
        return new Rates(parallel, parallelAt, official, generatedAt);
    }

    private static Long sideOf(Map<?, ?> market, Side side) {
        Object buy = market.get("buy");
        Object sell = market.get("sell");
        if (buy == null || sell == null) {
            return null;
        }
        return switch (side) {
            case BUY -> Micros.of(buy);
            case SELL -> Micros.of(sell);
            case MID -> Math.round((Micros.of(buy) + Micros.of(sell)) / 2.0);
        };
    }

    private static Optional<FetchedPrice> price(Long micros, Instant at) {
        return micros == null ? Optional.empty() : Optional.of(new FetchedPrice(micros, at));
    }
}
