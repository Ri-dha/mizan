package iq.mizan.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import iq.mizan.auth.service.LoggingCodeSender;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntryRepository;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private LoggingCodeSender codeSender;

    @Autowired
    private AuditEntryRepository auditEntries;

    @BeforeEach
    void reset() {
        resetTransactionalData();
    }

    @Test
    void registrationCreatesAnOwnedHouseholdAndAnUnverifiedAccount() {
        var session = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        JsonNode me = api.body(api.get("/api/v1/me", session.accessToken()));
        assertThat(me.get("email").asString()).isEqualTo(accounts.ownerEmail());
        assertThat(me.get("verified").asBoolean()).isFalse();
        assertThat(me.get("household").get("role").asString()).isEqualTo("OWNER");
        assertThat(me.get("household").get("baseCurrency").asString()).isEqualTo("IQD");
    }

    @Test
    void phoneNumbersAreNormalisedToE164() {
        var session = api.register("0770 000 0001", accounts.ownerPassword(), "Ridha");

        JsonNode me = api.body(api.get("/api/v1/me", session.accessToken()));
        assertThat(me.get("phone").asString()).isEqualTo(accounts.ownerPhone());
        assertThat(me.get("email").isNull()).isTrue();
    }

    @Test
    void duplicateIdentifierIsRefused() {
        api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        var result = api.post("/api/v1/auth/register", null, Map.of(
                "identifier", accounts.ownerEmail().toUpperCase(), "password", "another-pass-1", "displayName", "Twin"));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.body(result).get("code").asString()).isEqualTo("IDENTIFIER_ALREADY_REGISTERED");
    }

    @Test
    void contactIsVerifiedWithTheDeliveredCodeOnly() {
        var session = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");
        String delivered = codeSender.lastCode();

        var wrong = api.post("/api/v1/auth/verify", session.accessToken(), Map.of("code", "000000"));
        assertThat(wrong).hasStatus(HttpStatus.BAD_REQUEST);

        var right = api.post("/api/v1/auth/verify", session.accessToken(), Map.of("code", delivered));
        assertThat(right).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(api.body(api.get("/api/v1/me", session.accessToken())).get("verified").asBoolean()).isTrue();
        assertThat(api.post("/api/v1/auth/verify/resend", session.accessToken(), null)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void loginRejectsWrongPasswordAndLocksAfterRepeatedFailures() {
        api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(login(accounts.ownerEmail(), "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
        }
        assertThat(login(accounts.ownerEmail(), accounts.ownerPassword())).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("nobody@example.test", "whatever-1")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesAndReplayRevokesEverySession() {
        var session = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        var refreshed = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", session.refreshToken()));
        assertThat(refreshed).hasStatus(HttpStatus.OK);
        String rotated = api.body(refreshed).get("refreshToken").asString();
        assertThat(rotated).isNotEqualTo(session.refreshToken());

        var replay = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", session.refreshToken()));
        assertThat(replay).hasStatus(HttpStatus.UNAUTHORIZED);

        var afterReplay = api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", rotated));
        assertThat(afterReplay).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(auditEntries.findByActorIdAndActionOrderByOccurredAtDesc(
                java.util.UUID.fromString(api.body(api.get("/api/v1/me", session.accessToken())).get("id").asString()),
                AuditAction.TOKEN_REUSE_DETECTED)).isNotEmpty();
    }

    @Test
    void refreshTokenTravelsAsAnHttpOnlyCookie() {
        var result = api.post("/api/v1/auth/register", null, Map.of(
                "identifier", accounts.ownerEmail(), "password", accounts.ownerPassword(), "displayName", "Ridha"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("mizan_refresh=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");
    }

    @Test
    void passwordResetReplacesThePasswordAndEndsOtherSessions() {
        var session = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        assertThat(api.post("/api/v1/auth/password-reset/request", null, Map.of("identifier", accounts.ownerEmail())))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.post("/api/v1/auth/password-reset/request", null, Map.of("identifier", "ghost@example.test")))
                .hasStatus(HttpStatus.NO_CONTENT);

        var confirmed = api.post("/api/v1/auth/password-reset/confirm", null, Map.of(
                "identifier", accounts.ownerEmail(), "code", codeSender.lastCode(), "newPassword", "brand-new-pass-1"));
        assertThat(confirmed).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(login(accounts.ownerEmail(), accounts.ownerPassword())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(accounts.ownerEmail(), "brand-new-pass-1")).hasStatus(HttpStatus.OK);
        assertThat(api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", session.refreshToken())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointsRefuseAnonymousAndForgedTokens() {
        assertThat(api.get("/api/v1/me", null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.get("/api/v1/me", "not-a-token")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void householdSettingsCanBeChangedByTheOwner() {
        var session = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha");

        var updated = api.patch("/api/v1/households/current", session.accessToken(),
                Map.of("name", "Al-Tareq family", "monthStartDay", 25));

        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(api.body(updated).get("monthStartDay").asInt()).isEqualTo(25);
        assertThat(api.body(api.get("/api/v1/households/current", session.accessToken())).get("name").asString())
                .isEqualTo("Al-Tareq family");
    }

    private org.springframework.test.web.servlet.assertj.MvcTestResult login(String identifier, String password) {
        return api.post("/api/v1/auth/login", null, Map.of("identifier", identifier, "password", password));
    }

    /** In-app browsers send User-Agent strings past 256 characters; a session must still be issued. */
    @Test
    void registrationSurvivesAVeryLongUserAgent() {
        resetTransactionalData();
        String longUserAgent = "Mozilla/5.0 (Linux; Android 14) " + "X".repeat(700);
        var result = api.postWithHeaders("/api/v1/auth/register",
                Map.of("identifier", "ua@example.test", "password", accounts.ownerPassword(), "displayName", "Long UA"),
                Map.of("User-Agent", longUserAgent));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(api.body(result).get("accessToken").asString()).isNotBlank();
    }
}
