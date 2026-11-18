package iq.mizan.market.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.entity.PriceQuote;
import iq.mizan.market.repository.PriceQuoteRepository;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The quota is shared by every user, so the feed must spend it only when polled and stop at the reserve. */
class IqWealthFxFeedTest {

    private static final String URL = "https://iraqsm.example/api/v1";
    private static final String KEY = "iqw_test";
    private static final int RESERVE = 40;
    private static final Clock NOON_BAGHDAD = Clock.fixed(Instant.parse("2026-11-18T09:00:00Z"), ZoneOffset.UTC);

    private static final String BODY = """
            {"data": {"unit": "IQD per 1 USD",
                      "parallel": {"buy": 1700, "sell": 1704, "stale": false, "publishedAt": "2026-11-18T08:16:10.000Z"},
                      "official": {"cbi": 1510, "confirmed": "2026-10-07"}, "gapPct": 12.72},
             "asOf": "2026-11-18", "generatedAt": "2026-11-18T08:30:00.000Z"}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private final PriceQuoteRepository quotes = mock(PriceQuoteRepository.class);

    private IqWealthFxFeed feed(String key, String side) {
        MarketProperties.IqWealth config = new MarketProperties.IqWealth(URL, key, side, RESERVE, "Asia/Baghdad", "-", "-");
        MarketProperties.Feeds feeds = new MarketProperties.Feeds(null, null, null, null, 0, null, null, null, config);
        MarketSettings settings = new MarketSettings(null) {
            @Override
            public String iqWealthApiKey() {
                return key;
            }

            @Override
            public String iqWealthParallelSide() {
                return side;
            }

            @Override
            public int iqWealthReserve() {
                return RESERVE;
            }
        };
        return new IqWealthFxFeed(builder, new MarketProperties(false, null, null, 0, 0, null, feeds, null), settings, NOON_BAGHDAD, quotes);
    }

    private void answer(int remaining) {
        server.expect(requestTo(URL + "/fx")).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KEY))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON).header(IqWealthFxFeed.REMAINING_HEADER, String.valueOf(remaining)));
    }

    @Test
    void answersFromTheLastPollWithoutTouchingTheNetwork() {
        IqWealthFxFeed feed = feed(KEY, "MID");
        assertThat(feed.fetch(Instrument.USDIQD_PARALLEL)).isEmpty();

        answer(99);
        assertThat(feed.poll()).isEqualTo(IqWealthFxFeed.Outcome.UPDATED);
        server.verify();

        assertThat(feed.fetch(Instrument.USDIQD_PARALLEL)).get()
                .satisfies(price -> {
                    assertThat(price.priceMicros()).isEqualTo(1_702_000_000L);
                    assertThat(price.quotedAt()).isEqualTo(Instant.parse("2026-11-18T08:16:10Z"));
                });
        assertThat(feed.fetch(Instrument.USDIQD_OFFICIAL)).get().extracting(FetchedPrice::priceMicros).isEqualTo(1_510_000_000L);
        assertThat(feed.supports(Instrument.XAU)).isFalse();
    }

    @Test
    void anInstanceThatDidNotPollAnswersWithTheStoredQuote() {
        Instant quotedAt = Instant.parse("2026-11-18T08:00:00Z");
        when(quotes.findById(Instrument.USDIQD_PARALLEL))
                .thenReturn(Optional.of(PriceQuote.of(Instrument.USDIQD_PARALLEL, 1_700_000_000L, IqWealthFxFeed.NAME, quotedAt, quotedAt)));
        when(quotes.findById(Instrument.USDIQD_OFFICIAL))
                .thenReturn(Optional.of(PriceQuote.of(Instrument.USDIQD_OFFICIAL, 1_310_000_000L, "cbi.iq", quotedAt, quotedAt)));

        assertThat(feed(KEY, "MID").fetch(Instrument.USDIQD_PARALLEL)).get().extracting(FetchedPrice::quotedAt).isEqualTo(quotedAt);
        assertThat(feed(KEY, "MID").fetch(Instrument.USDIQD_OFFICIAL)).isEmpty();
        assertThat(feed("", "MID").fetch(Instrument.USDIQD_PARALLEL)).isEmpty();
    }

    @Test
    void theBuyOrSellSideCanBeChosen() {
        assertThat(IqWealthFxFeed.parse(parsedBody(), IqWealthFxFeed.Side.BUY).parallelMicros()).isEqualTo(1_700_000_000L);
        assertThat(IqWealthFxFeed.parse(parsedBody(), IqWealthFxFeed.Side.SELL).parallelMicros()).isEqualTo(1_704_000_000L);
    }

    @Test
    void aStaleMarketRateIsLeftOutButTheOfficialRateStays() {
        var body = java.util.Map.of("data", java.util.Map.of(
                "parallel", java.util.Map.of("buy", 1700, "sell", 1700, "stale", true),
                "official", java.util.Map.of("cbi", 1510)), "generatedAt", "2026-11-18T08:30:00Z");
        IqWealthFxFeed.Rates rates = IqWealthFxFeed.parse(body, IqWealthFxFeed.Side.MID);
        assertThat(rates.parallelMicros()).isNull();
        assertThat(rates.officialMicros()).isEqualTo(1_510_000_000L);
    }

    @Test
    void pollingStopsAtTheReserveForTheRestOfTheDay() {
        IqWealthFxFeed feed = feed(KEY, "MID");
        answer(RESERVE);
        assertThat(feed.poll()).isEqualTo(IqWealthFxFeed.Outcome.UPDATED);
        assertThat(feed.poll()).isEqualTo(IqWealthFxFeed.Outcome.BUDGET_KEPT);
        server.verify();
    }

    @Test
    void theDailyLimitPausesAndARefusedKeySwitchesTheFeedOff() {
        IqWealthFxFeed limited = feed(KEY, "MID");
        server.expect(ExpectedCount.once(), requestTo(URL + "/fx")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(limited.poll()).isEqualTo(IqWealthFxFeed.Outcome.PAUSED_UNTIL_MIDNIGHT);
        assertThat(limited.poll()).isEqualTo(IqWealthFxFeed.Outcome.PAUSED_UNTIL_MIDNIGHT);
        server.verify();
        server.reset();

        IqWealthFxFeed refused = feed(KEY, "MID");
        server.expect(ExpectedCount.once(), requestTo(URL + "/fx")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThat(refused.poll()).isEqualTo(IqWealthFxFeed.Outcome.KEY_REFUSED);
        assertThat(refused.poll()).isEqualTo(IqWealthFxFeed.Outcome.KEY_REFUSED);
        server.verify();
    }

    @Test
    void withoutAKeyNothingIsCalled() {
        assertThat(feed("", "MID").poll()).isEqualTo(IqWealthFxFeed.Outcome.NO_KEY);
        server.verify();
    }

    private static java.util.Map<?, ?> parsedBody() {
        return tools.jackson.databind.json.JsonMapper.builder().build().readValue(BODY, java.util.Map.class);
    }
}
