package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import iq.mizan.market.feed.LocalDealerPriceFeed;

import org.junit.jupiter.api.Test;

class LocalDealerPriceFeedTest {

    @Test
    void aMithqalOf21kBecomesAPricePerGramOfPureGold() {
        long perMithqal21k = 547_939_000_000L;
        assertThat(LocalDealerPriceFeed.toPerGram24kMicros(perMithqal21k, LocalDealerPriceFeed.Unit.MITHQAL_21K)).isEqualTo(125_243_200_000L);
        assertThat(LocalDealerPriceFeed.toPerGram24kMicros(1_000_000_000L, LocalDealerPriceFeed.Unit.GRAM_24K)).isEqualTo(1_001_001_001L);
    }

    @Test
    void dotPathsReachNestedNumbers() {
        Map<String, Object> body = Map.of("prices", Map.of("gold", Map.of("mithqal21", 547000)));
        var dig = LocalDealerPriceFeedTestAccess.dig(body, "prices.gold.mithqal21");
        assertThat(dig).isEqualTo(547000);
        assertThat(LocalDealerPriceFeedTestAccess.dig(body, "prices.silver")).isNull();
    }
}
