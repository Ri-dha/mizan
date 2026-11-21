package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import iq.mizan.market.feed.FrankfurterClient.DayRate;
import iq.mizan.market.service.WorldRateService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class WorldRateIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-11-16");
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private WorldRateService worldRates;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void reset() {
        resetTransactionalData();
        jdbc.execute("truncate table world_rate");
        token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        worldRates.store(List.of(
                new DayRate("EUR", MONDAY, new BigDecimal("0.90")),
                new DayRate("EUR", TUESDAY, new BigDecimal("0.91")),
                new DayRate("TRY", MONDAY, new BigDecimal("50.2"))));
    }

    @Test
    void eachCurrencyShowsItsLatestDay() {
        JsonNode body = api.body(api.get("/api/v1/market/world-rates", token));

        assertThat(body.get("source").asString()).isEqualTo("frankfurter.dev");
        JsonNode rates = body.get("rates");
        assertThat(rates).hasSize(2);
        assertThat(rates.get(0).get("currency").asString()).isEqualTo("EUR");
        assertThat(rates.get(0).get("perUsd").decimalValue()).isEqualByComparingTo("0.91");
        assertThat(rates.get(0).get("day").asString()).isEqualTo(TUESDAY.toString());
        assertThat(rates.get(1).get("day").asString()).isEqualTo(MONDAY.toString());
    }

    @Test
    void aLateCorrectionReplacesTheDayInsteadOfAddingOne() {
        worldRates.store(List.of(new DayRate("EUR", TUESDAY, new BigDecimal("0.92"))));

        JsonNode history = api.body(api.get("/api/v1/market/world-rates/history?currency=EUR&from=2026-11-01&to=2026-11-30", token));

        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("perUsd").decimalValue()).isEqualByComparingTo("0.92");
    }

    @Test
    void historyRefusesALowercaseCodeAndAnEndlessRange() {
        assertThat(api.get("/api/v1/market/world-rates/history?currency=eur", token)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.get("/api/v1/market/world-rates/history?currency=EUR&from=2010-01-01&to=2026-11-30", token)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ratesNeedASignIn() {
        assertThat(api.get("/api/v1/market/world-rates", null)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
