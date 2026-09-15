package iq.mizan.market.feed;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.entity.Instrument;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Phase 4: a local market quote from any JSON endpoint an operator points at (a dealer's site,
 * or a file they maintain). Paths pick the numbers out; the unit says what one number means,
 * and it is normalised to IQD per gram of pure metal so valuation treats it like an override.
 */
@Component
@Order(5)
@ConditionalOnProperty(prefix = "mizan.market.feeds.local-dealer", name = "url")
public class LocalDealerPriceFeed implements PriceFeed {

    private static final long BASIS_POINTS = 10_000L;
    private static final long MILLIGRAMS_PER_GRAM = 1_000L;

    /** What one quoted number is a price of: a weight of a purity. */
    public enum Unit {
        GRAM_24K(1_000, 9_990), GRAM_22K(1_000, 9_160), GRAM_21K(1_000, 8_750), GRAM_18K(1_000, 7_500),
        MITHQAL_24K(5_000, 9_990), MITHQAL_22K(5_000, 9_160), MITHQAL_21K(5_000, 8_750), MITHQAL_18K(5_000, 7_500),
        GRAM_999(1_000, 9_990), GRAM_925(1_000, 9_250);

        final long milligrams;
        final int purityBasisPoints;

        Unit(long milligrams, int purityBasisPoints) {
            this.milligrams = milligrams;
            this.purityBasisPoints = purityBasisPoints;
        }
    }

    private final RestClient client;
    private final MarketProperties.LocalDealer config;
    private final Clock clock;

    public LocalDealerPriceFeed(RestClient.Builder builder, MarketProperties properties, Clock clock) {
        this.config = properties.feeds().localDealer();
        this.client = builder.build();
        this.clock = clock;
    }

    @Override
    public String name() {
        return "local-dealer";
    }

    @Override
    public boolean supports(Instrument instrument) {
        return Configured.present(config.url())
                && (instrument == Instrument.XAU_LOCAL && Configured.present(config.goldPath())
                    || instrument == Instrument.XAG_LOCAL && Configured.present(config.silverPath()));
    }

    @Override
    public Optional<FetchedPrice> fetch(Instrument instrument) {
        Map<?, ?> body = client.get().uri(config.url()).retrieve().body(Map.class);
        String path = instrument == Instrument.XAU_LOCAL ? config.goldPath() : config.silverPath();
        Unit unit = Unit.valueOf(instrument == Instrument.XAU_LOCAL ? config.goldUnit() : config.silverUnit());
        Object value = dig(body, path);
        return value == null ? Optional.empty()
                : Optional.of(new FetchedPrice(toPerGram24kMicros(Micros.of(value), unit), clock.instant()));
    }

    /** "IQD per unit" → IQD micros per gram of pure metal: price ÷ (grams × purity), rounded half-up. */
    public static long toPerGram24kMicros(long priceMicrosPerUnit, Unit unit) {
        BigInteger numerator = BigInteger.valueOf(priceMicrosPerUnit).multiply(BigInteger.valueOf(BASIS_POINTS * MILLIGRAMS_PER_GRAM));
        BigInteger divisor = BigInteger.valueOf(unit.milligrams * unit.purityBasisPoints);
        return numerator.add(divisor.shiftRight(1)).divide(divisor).longValueExact();
    }

    static Object dig(Map<?, ?> body, String path) {
        Object current = body;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }
}
