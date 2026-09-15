package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.feed.GoldApiComPriceFeed;

import org.junit.jupiter.api.Test;

class GoldApiComPriceFeedTest {

    @Test
    void readsPriceAndTimestamp() throws Exception {
        var parse = GoldApiComPriceFeed.class.getDeclaredMethod("parse", Map.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        var fetched = (java.util.Optional<FetchedPrice>) parse.invoke(null, Map.of("price", 4289.100098, "symbol", "XAU", "updatedAt", "2026-09-14T23:46:39Z"));
        assertThat(fetched).isPresent();
        assertThat(fetched.get().priceMicros()).isEqualTo(4_289_100_098L);
        assertThat(fetched.get().quotedAt()).isEqualTo(Instant.parse("2026-09-14T23:46:39Z"));
        @SuppressWarnings("unchecked")
        var empty = (java.util.Optional<FetchedPrice>) parse.invoke(null, Map.of("error", "rate limited"));
        assertThat(empty).isEmpty();
    }
}
