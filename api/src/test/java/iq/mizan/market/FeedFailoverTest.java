package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.market.feed.PriceFeed;

import org.junit.jupiter.api.Test;

/** FR-MKT-06: a provider that throws or answers empty hands over to the next one. */
class FeedFailoverTest {

    private static PriceFeed feed(String name, boolean throwing, Long price) {
        return new PriceFeed() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean supports(Instrument instrument) {
                return instrument == Instrument.XAU;
            }

            @Override
            public Optional<FetchedPrice> fetch(Instrument instrument) {
                if (throwing) throw new IllegalStateException("down");
                return Optional.ofNullable(price).map(p -> new FetchedPrice(p, Instant.EPOCH));
            }
        };
    }

    @Test
    void firstAnsweringFeedWins() {
        List<PriceFeed> feeds = List.of(feed("down", true, null), feed("empty", false, null), feed("good", false, 5L), feed("later", false, 6L));

        Optional<PriceFeed> chosen = feeds.stream().filter(f -> {
            try {
                return f.fetch(Instrument.XAU).isPresent();
            } catch (RuntimeException e) {
                return false;
            }
        }).findFirst();

        assertThat(chosen).map(PriceFeed::name).contains("good");
    }
}
