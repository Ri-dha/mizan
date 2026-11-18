package iq.mizan.market.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import iq.mizan.market.entity.Instrument;
import iq.mizan.market.feed.FetchedPrice;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** T-price-history: a change is kept forever, a repeat is not, and every signed-in role can read prices. */
class PriceHistoryIntegrationTest extends PostgresIntegrationTest {

    private static final Instrument DOLLAR = Instrument.USDIQD_PARALLEL;

    @Autowired
    private MarketService marketService;

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;

    @BeforeEach
    void emptyMarket() {
        resetTransactionalData();
        jdbc.execute("delete from price_tick; delete from price_history; delete from price_quote");
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
    }

    private void observe(String at, long iqd) {
        marketService.store(DOLLAR, "iraqsm.com", new FetchedPrice(iqd * 1_000_000L, Instant.parse(at)));
    }

    @Test
    void onlyAChangeIsRecordedAndTheDayKeepsItsRange() {
        observe("2026-11-17T08:00:00Z", 1_700);
        observe("2026-11-17T09:00:00Z", 1_700);
        observe("2026-11-17T10:00:00Z", 1_710);
        observe("2026-11-17T11:00:00Z", 1_695);
        observe("2026-11-17T12:00:00Z", 1_702);

        assertThat(jdbc.queryForObject("select count(*) from price_tick where instrument = ?", Long.class, DOLLAR.name())).isEqualTo(4);
        Map<String, Object> day = jdbc.queryForMap("select open_micros, high_micros, low_micros, price_micros from price_history where instrument = ?", DOLLAR.name());
        assertThat(day).containsEntry("open_micros", 1_700_000_000L).containsEntry("high_micros", 1_710_000_000L)
                .containsEntry("low_micros", 1_695_000_000L).containsEntry("price_micros", 1_702_000_000L);
    }

    @Test
    void historyRollsUpByWeekFromSaturdayAndByMonth() {
        observe("2026-10-30T10:00:00Z", 1_650);
        observe("2026-10-31T10:00:00Z", 1_660);
        observe("2026-11-02T10:00:00Z", 1_690);
        observe("2026-11-04T10:00:00Z", 1_670);

        JsonNode weeks = api.body(api.get("/api/v1/market/history?instrument=USDIQD_PARALLEL&from=2026-10-01&to=2026-11-30&interval=WEEK", owner));
        assertThat(weeks).hasSize(2);
        assertThat(weeks.get(0).get("day").asString()).isEqualTo("2026-10-24");
        assertThat(weeks.get(1).get("day").asString()).isEqualTo("2026-10-31");
        assertThat(weeks.get(1).get("openMicros").asLong()).isEqualTo(1_660_000_000L);
        assertThat(weeks.get(1).get("highMicros").asLong()).isEqualTo(1_690_000_000L);
        assertThat(weeks.get(1).get("priceMicros").asLong()).isEqualTo(1_670_000_000L);

        JsonNode months = api.body(api.get("/api/v1/market/history?instrument=USDIQD_PARALLEL&from=2026-10-01&to=2026-11-30&interval=MONTH", owner));
        assertThat(months).hasSize(2);
        assertThat(months.get(1).get("day").asString()).isEqualTo("2026-11-01");
        assertThat(months.get(1).get("lowMicros").asLong()).isEqualTo(1_670_000_000L);
    }

    @Test
    void changesComeNewestFirstWithWhatTheyReplacedAndPageCleanly() {
        long[] prices = {1_700, 1_705, 1_699, 1_702, 1_710};
        for (int i = 0; i < prices.length; i++) {
            observe("2026-11-18T0%d:00:00Z".formatted(i + 1), prices[i]);
        }

        JsonNode first = api.body(api.get("/api/v1/market/changes?instrument=USDIQD_PARALLEL&limit=2", owner));
        assertThat(first.get("changes")).hasSize(2);
        assertThat(first.get("changes").get(0).get("priceMicros").asLong()).isEqualTo(1_710_000_000L);
        assertThat(first.get("changes").get(0).get("previousMicros").asLong()).isEqualTo(1_702_000_000L);
        assertThat(first.get("changes").get(1).get("previousMicros").asLong()).isEqualTo(1_699_000_000L);

        List<Long> seen = new ArrayList<>();
        String before = first.get("nextBefore").asString();
        first.get("changes").forEach(change -> seen.add(change.get("priceMicros").asLong()));
        while (before != null) {
            JsonNode page = api.body(api.get("/api/v1/market/changes?instrument=USDIQD_PARALLEL&limit=2&before=" + before, owner));
            page.get("changes").forEach(change -> seen.add(change.get("priceMicros").asLong()));
            before = page.get("nextBefore").isNull() ? null : page.get("nextBefore").asString();
            if (before == null) {
                assertThat(page.get("changes").get(page.get("changes").size() - 1).get("previousMicros").isNull()).isTrue();
            }
        }
        assertThat(seen).containsExactly(1_710_000_000L, 1_702_000_000L, 1_699_000_000L, 1_705_000_000L, 1_700_000_000L);
    }

    @Test
    void aTickCannotBeRewritten() {
        observe("2026-11-18T08:00:00Z", 1_700);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update price_tick set price_micros = 1"))
                .hasMessageContaining("cannot be changed");
    }

    @Test
    void dependentsAndAdvisorsCanReadPrices() {
        observe("2026-11-18T08:00:00Z", 1_700);
        ApiClient.Session other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        for (String role : List.of("DEPENDENT", "ADVISOR")) {
            jdbc.update("delete from membership where user_id = (select id from app_user where email = ?) and role <> 'OWNER'", accounts.otherEmail());
            JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", role)));
            api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", other.accessToken(), null));
            other = api.refresh(other.refreshToken());
            assertThat(api.get("/api/v1/market/changes?instrument=USDIQD_PARALLEL", other.accessToken())).hasStatus(HttpStatus.OK);
            assertThat(api.get("/api/v1/market/history?instrument=USDIQD_PARALLEL", other.accessToken())).hasStatus(HttpStatus.OK);
        }
    }
}
