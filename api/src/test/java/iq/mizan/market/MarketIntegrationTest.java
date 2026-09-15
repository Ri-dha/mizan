package iq.mizan.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import iq.mizan.market.service.MarketService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class MarketIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void reset() {
        resetTransactionalData();
        marketService.refresh();
        token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
    }

    @Test
    void quotesCarrySourceAndFreshnessForEveryInstrument() {
        JsonNode quotes = api.body(api.get("/api/v1/market/quotes", token));

        assertThat(quotes).hasSize(7);
        for (JsonNode quote : quotes) {
            assertThat(quote.get("source").asString()).isEqualTo("stub");
            assertThat(quote.get("stale").asBoolean()).isFalse();
        }
        JsonNode gold = null;
        for (JsonNode quote : quotes) {
            if ("XAU".equals(quote.get("instrument").asString())) gold = quote;
        }
        assertThat(gold).isNotNull();
        assertThat(gold.get("priceMicros").asLong()).isEqualTo(2_650_000_000L);
    }

    @Test
    void aQuoteOlderThanADayIsMarkedStaleButStillServed() {
        jdbc.update("update price_quote set fetched_at = ? where instrument = 'XAG'",
                java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)));

        JsonNode quotes = api.body(api.get("/api/v1/market/quotes", token));

        for (JsonNode quote : quotes) {
            boolean silver = "XAG".equals(quote.get("instrument").asString());
            assertThat(quote.get("stale").asBoolean()).isEqualTo(silver);
        }
    }

    @Test
    void refreshRecordsOneHistoryPointPerDay() {
        marketService.refresh();

        JsonNode history = api.body(api.get("/api/v1/market/history?instrument=XAU", token));

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("priceMicros").asLong()).isEqualTo(2_650_000_000L);
    }

    @Test
    void anonymousCallersGetNothing() {
        assertThat(api.get("/api/v1/market/quotes", null)).hasStatus(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}
