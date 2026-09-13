package iq.mizan.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import iq.mizan.auth.service.AccountDeletionService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class AccountDeletionIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private AccountDeletionService deletionService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        resetTransactionalData();
    }

    @Test
    void aRequestCanBeWithdrawnWithinTheGracePeriodAndStillSignsIn() {
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();

        JsonNode requested = api.body(api.post("/api/v1/me/deletion-request", token, null));
        assertThat(requested.get("deletionRequestedAt").isNull()).isFalse();
        assertThat(api.post("/api/v1/auth/login", null, Map.of("identifier", accounts.ownerEmail(), "password", accounts.ownerPassword())))
                .hasStatus(HttpStatus.OK);

        assertThat(deletionService.purgeExpired()).isZero();

        JsonNode cancelled = api.body(api.delete("/api/v1/me/deletion-request", token));
        assertThat(cancelled.get("deletionRequestedAt").isNull()).isTrue();
    }

    @Test
    void thePurgeRemovesTheAccountAndItsHouseholdOnceTheWindowHasPassed() {
        String token = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        api.post("/api/v1/me/deletion-request", token, null);
        jdbc.update("update app_user set deletion_requested_at = ?", java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)));

        assertThat(deletionService.purgeExpired()).isEqualTo(1);

        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from household", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from bucket", Long.class)).isZero();
        assertThat(api.get("/api/v1/me", token)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
