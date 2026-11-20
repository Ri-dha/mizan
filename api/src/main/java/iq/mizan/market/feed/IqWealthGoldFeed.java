package iq.mizan.market.feed;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.repository.PriceQuoteRepository;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * IQWealth (iraqsm.com) {@code GET /gold}: the 24-karat gram in dinars, which is the local gold
 * price per gram of pure metal, and the world ounce in dollars. It publishes a daily price, so it
 * is polled a few times a day through the shared {@link IqWealthClient} and answers from the last
 * poll. It has no dealer bid and no silver; those come from the other feeds. Whether it is tried
 * before or after the dealer feeds is the operator's choice ({@code MARKET_GOLD_PRIMARY}).
 */
@Component
@Order(15)
public class IqWealthGoldFeed implements PriceFeed {

    public static final String NAME = IqWealthClient.NAME;
    private static final int PURE_KARAT = 24;

    record Gold(Long localGramMicros, Long ounceUsdMicros, Instant at) {
    }

    private final IqWealthClient iqWealth;
    private final StoredQuotes stored;
    private volatile Gold gold;

    public IqWealthGoldFeed(IqWealthClient iqWealth, PriceQuoteRepository quotes) {
        this.iqWealth = iqWealth;
        this.stored = new StoredQuotes(quotes, iqWealth);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean supports(Instrument instrument) {
        return instrument == Instrument.XAU || instrument == Instrument.XAU_LOCAL;
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Gold known = gold;
        if (known == null) {
            return stored.last(instrument);
        }
        Long micros = instrument == Instrument.XAU_LOCAL ? known.localGramMicros() : known.ounceUsdMicros();
        return micros == null ? Optional.empty() : Optional.of(new FetchedPrice(micros, known.at()));
    }

    public boolean hasPrices() {
        return gold != null;
    }

    public synchronized IqWealthClient.Outcome poll() {
        IqWealthClient.Reply reply = iqWealth.get("/gold");
        if (!reply.answered()) {
            return iqWealth.recorded(reply.skipped());
        }
        Gold next = parse(reply.body());
        boolean changed = !next.equals(gold);
        gold = next;
        return iqWealth.recorded(changed ? IqWealthClient.Outcome.UPDATED : IqWealthClient.Outcome.UNCHANGED);
    }

    /**
     * {"data": {"gramByCarat": [{"karat", "iqd", "usd", "mithqalIqd"}], "ounce": {"buy": {"usd"},
     * "sell": {"usd"}}}, "generatedAt"}; the ounce is the middle of buy and sell.
     */
    static Gold parse(Map<?, ?> body) {
        if (body == null || !(body.get("data") instanceof Map<?, ?> data)) {
            throw new IllegalStateException("IQWealth /gold answered without data");
        }
        Instant at = body.get("generatedAt") instanceof String text ? Instant.parse(text) : Instant.now();
        Long gram = null;
        if (data.get("gramByCarat") instanceof List<?> karats) {
            for (Object entry : karats) {
                if (entry instanceof Map<?, ?> karat && karat.get("karat") instanceof Number k && k.intValue() == PURE_KARAT
                        && karat.get("iqd") != null) {
                    gram = Micros.of(karat.get("iqd"));
                }
            }
        }
        Long ounce = null;
        if (data.get("ounce") instanceof Map<?, ?> o && o.get("buy") instanceof Map<?, ?> buy && o.get("sell") instanceof Map<?, ?> sell
                && buy.get("usd") != null && sell.get("usd") != null) {
            ounce = Math.round((Micros.of(buy.get("usd")) + Micros.of(sell.get("usd"))) / 2.0);
        }
        return new Gold(gram, ounce, at);
    }
}
