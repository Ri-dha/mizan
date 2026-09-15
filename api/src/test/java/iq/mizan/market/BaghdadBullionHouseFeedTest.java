package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.BaghdadBullionHouseFeed;
import iq.mizan.market.feed.FetchedPrice;

import org.junit.jupiter.api.Test;

class BaghdadBullionHouseFeedTest {

    private static final Map<String, Object> BODY = Map.of(
            "as_of", "2026-09-15T00:04:26.689Z", "stale", false,
            "base", Map.of("gold_iqd_g", Map.of("bid", 216424.63, "ask", 216895.63), "silver_iqd_g", Map.of("bid", 3502.1, "ask", 3235.2)));

    @Test
    void askFeedsTheLocalPriceAndBidTheBuyBack() throws Exception {
        assertThat(parse(BODY, Instrument.XAU_LOCAL)).map(FetchedPrice::priceMicros).contains(216_895_630_000L);
        assertThat(parse(BODY, Instrument.XAU_LOCAL_BID)).map(FetchedPrice::priceMicros).contains(216_424_630_000L);
        assertThat(parse(BODY, Instrument.XAG_LOCAL)).map(FetchedPrice::priceMicros).contains(3_235_200_000L);
        assertThat(parse(BODY, Instrument.XAG_LOCAL_BID)).map(FetchedPrice::quotedAt).contains(Instant.parse("2026-09-15T00:04:26.689Z"));
        assertThat(parse(Map.of("as_of", "2026-09-15T00:04:26.689Z"), Instrument.XAU_LOCAL)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Optional<FetchedPrice> parse(Map<String, Object> body, Instrument instrument) throws Exception {
        Method method = BaghdadBullionHouseFeed.class.getDeclaredMethod("parse", Map.class, Instrument.class);
        method.setAccessible(true);
        return (Optional<FetchedPrice>) method.invoke(null, body, instrument);
    }
}
