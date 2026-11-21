package iq.mizan.household;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import iq.mizan.market.feed.FrankfurterClient.DayRate;
import iq.mizan.market.service.MarketService;
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

/** ADR currency-1: country and currency at sign-up, and a base change converted once per member. */
class BaseCurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.parse("2026-11-20");

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private WorldRateService worldRates;

    @Autowired
    private MarketService market;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        resetTransactionalData();
        jdbc.execute("truncate table world_rate, household_rebase");
        worldRates.store(List.of(
                new DayRate("EUR", DAY, new BigDecimal("0.9")),
                new DayRate("TRY", DAY, new BigDecimal("50")),
                new DayRate("JOD", DAY, new BigDecimal("0.709"))));
        market.refresh();
    }

    @Test
    void theCatalogueIsOpenBeforeSignInAndLeavesOutTheExcludedCountry() {
        JsonNode body = api.body(api.get("/api/v1/market/currencies", null));

        assertThat(body.get("currencies").toString()).contains("\"EUR\"", "\"IQD\"", "\"USD\"").doesNotContain("ILS");
        String countries = body.get("countries").toString();
        assertThat(countries).contains("{\"code\":\"IQ\",\"currency\":\"IQD\"}", "{\"code\":\"TR\",\"currency\":\"TRY\"}",
                "{\"code\":\"PS\",\"currency\":\"JOD\"}", "{\"code\":\"DE\",\"currency\":\"EUR\"}");
        assertThat(countries).doesNotContain("\"IL\"");
    }

    @Test
    void registrationTakesTheCountryAndItsCurrencyOrAnotherOffered() {
        String turk = api.body(api.registerIn(accounts.ownerEmail(), accounts.ownerPassword(), "Ayse", "TR", null)).get("accessToken").asString();
        JsonNode me = api.body(api.get("/api/v1/me", turk));
        assertThat(me.get("country").asString()).isEqualTo("TR");
        assertThat(me.get("household").get("baseCurrency").asString()).isEqualTo("TRY");

        String iraqi = api.body(api.registerIn(accounts.otherEmail(), accounts.otherPassword(), "Ali", "IQ", "USD")).get("accessToken").asString();
        assertThat(api.body(api.get("/api/v1/me", iraqi)).get("household").get("baseCurrency").asString()).isEqualTo("USD");
    }

    @Test
    void anExcludedOrUnknownChoiceIsRefused() {
        assertThat(api.registerIn(accounts.ownerEmail(), accounts.ownerPassword(), "X", "IL", null)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.registerIn(accounts.ownerEmail(), accounts.ownerPassword(), "X", "IQ", "ILS")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.registerIn(accounts.ownerEmail(), accounts.ownerPassword(), "X", "IQ", "XYZ")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anOlderAppWithoutTheFieldsStillGetsIraqAndTheDinar() {
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        JsonNode me = api.body(api.get("/api/v1/me", token));
        assertThat(me.get("country").asString()).isEqualTo("IQ");
        assertThat(me.get("household").get("baseCurrency").asString()).isEqualTo("IQD");
        assertThat(me.get("trackedCurrencies")).isEmpty();
    }

    @Test
    void theProfileKeepsTheCountryAndTheFollowedCurrencies() {
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();

        JsonNode me = api.body(api.patch("/api/v1/me", token, Map.of("country", "DE", "trackedCurrencies", List.of("EUR", "TRY", "EUR"))));

        assertThat(me.get("country").asString()).isEqualTo("DE");
        assertThat(me.get("trackedCurrencies").toString()).isEqualTo("[\"EUR\",\"TRY\"]");
        assertThat(api.patch("/api/v1/me", token, Map.of("trackedCurrencies", List.of("ILS")))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aBaseChangeIsConvertedOncePerMemberWithEveryFactorSinceTheirLastClaim() {
        String owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();

        JsonNode toDollars = api.body(api.put("/api/v1/households/current/base-currency", owner, Map.of("currency", "USD", "factor", "0.00068")));
        assertThat(toDollars.get("baseCurrency").asString()).isEqualTo("USD");
        assertThat(toDollars.get("baseGeneration").asInt()).isEqualTo(1);
        assertThat(toDollars.get("rebasedGeneration").asInt()).isEqualTo(1);
        assertThat(api.body(api.post("/api/v1/households/current/rebase-claims", owner, null)).get("claimed").asBoolean()).isFalse();

        api.put("/api/v1/households/current/base-currency", owner, Map.of("currency", "EUR", "factor", "0.9"));
        jdbc.update("update membership set rebased_generation = 0");

        JsonNode claim = api.body(api.post("/api/v1/households/current/rebase-claims", owner, null));
        assertThat(claim.get("claimed").asBoolean()).isTrue();
        assertThat(claim.get("generation").asInt()).isEqualTo(2);
        assertThat(claim.get("fromCurrency").asString()).isEqualTo("IQD");
        assertThat(claim.get("baseCurrency").asString()).isEqualTo("EUR");
        assertThat(claim.get("factor").decimalValue()).isEqualByComparingTo("0.000612");
        assertThat(api.body(api.post("/api/v1/households/current/rebase-claims", owner, null)).get("claimed").asBoolean()).isFalse();
    }

    @Test
    void aFactorFarFromTodaysRatesOrTheSameCurrencyIsRefused() {
        String owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();

        assertThat(api.put("/api/v1/households/current/base-currency", owner, Map.of("currency", "USD", "factor", "1"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.put("/api/v1/households/current/base-currency", owner, Map.of("currency", "IQD", "factor", "1"))).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
