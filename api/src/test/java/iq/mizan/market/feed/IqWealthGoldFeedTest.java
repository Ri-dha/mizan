package iq.mizan.market.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import iq.mizan.market.MarketProperties;
import iq.mizan.market.MarketSettings;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.repository.PriceQuoteRepository;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ADR prices-3: IQWealth gold, read through the same daily budget as the dollar. */
class IqWealthGoldFeedTest {

    private static final String URL = "https://iraqsm.example/api/v1";
    private static final String KEY = "iqw_test";
    private static final int RESERVE = 30;
    private static final Clock NOON_BAGHDAD = Clock.fixed(Instant.parse("2026-11-20T09:00:00Z"), ZoneOffset.UTC);

    /** The shape of a real answer (2026-11-20), trimmed. */
    private static final String GOLD = """
            {"data": {"unit": "IQD and USD",
                      "gramByCarat": [{"karat": 24, "iqd": 192742, "usd": 112.95, "mithqalIqd": 963710},
                                      {"karat": 21, "iqd": 168649, "usd": 98.83, "mithqalIqd": 843245}],
                      "ounce": {"sell": {"iqd": 5994935, "usd": 3513}, "buy": {"iqd": 5996641, "usd": 3514}}},
             "asOf": "2026/11/20", "generatedAt": "2026-11-20T08:00:14.670Z"}
            """;
    private static final String FX = """
            {"data": {"parallel": {"buy": 1700, "sell": 1704, "publishedAt": "2026-11-20T08:16:10.000Z"},
                      "official": {"cbi": 1510}}, "generatedAt": "2026-11-20T08:30:00.000Z"}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PriceQuoteRepository quotes = mock(PriceQuoteRepository.class);
    private final MarketSettings settings = new MarketSettings(null) {
        @Override
        public String iqWealthApiKey() {
            return KEY;
        }

        @Override
        public String iqWealthParallelSide() {
            return "MID";
        }

        @Override
        public int iqWealthReserve() {
            return RESERVE;
        }
    };
    private final IqWealthClient client = new IqWealthClient(builder, new MarketProperties(false, null, null, 0, 0, null,
            new MarketProperties.Feeds(null, null, null, null, 0, null, null, null,
                    new MarketProperties.IqWealth(URL, KEY, "MID", RESERVE, "Asia/Baghdad", "-", "-", "-")), null), settings, NOON_BAGHDAD);

    @Test
    void takesThePureGramInDinarsAndTheMiddleOfTheOunceAndAnswersFromThePoll() {
        IqWealthGoldFeed feed = new IqWealthGoldFeed(client, quotes);
        server.expect(requestTo(URL + "/gold")).andRespond(withSuccess(GOLD, MediaType.APPLICATION_JSON).header(IqWealthClient.REMAINING_HEADER, "90"));

        assertThat(feed.poll()).isEqualTo(IqWealthClient.Outcome.UPDATED);
        server.verify();

        assertThat(feed.supports(Instrument.XAU_LOCAL)).isTrue();
        assertThat(feed.supports(Instrument.XAU_LOCAL_BID)).isFalse();
        assertThat(feed.supports(Instrument.XAG)).isFalse();
        assertThat(feed.fetch(Instrument.XAU_LOCAL)).get().extracting(FetchedPrice::priceMicros).isEqualTo(192_742_000_000L);
        assertThat(feed.fetch(Instrument.XAU)).get().extracting(FetchedPrice::priceMicros).isEqualTo(3_513_500_000L);
        assertThat(feed.fetch(Instrument.XAU)).get().extracting(FetchedPrice::quotedAt).isEqualTo(Instant.parse("2026-11-20T08:00:14.670Z"));
    }

    @Test
    void sharesTheDollarsDailyBudget() {
        IqWealthFxFeed dollar = new IqWealthFxFeed(client, settings, quotes);
        IqWealthGoldFeed gold = new IqWealthGoldFeed(client, quotes);
        server.expect(ExpectedCount.once(), requestTo(URL + "/fx"))
                .andRespond(withSuccess(FX, MediaType.APPLICATION_JSON).header(IqWealthClient.REMAINING_HEADER, String.valueOf(RESERVE)));

        assertThat(dollar.poll()).isEqualTo(IqWealthClient.Outcome.UPDATED);
        assertThat(gold.poll()).isEqualTo(IqWealthClient.Outcome.BUDGET_KEPT);
        server.verify();
    }
}
