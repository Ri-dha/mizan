package iq.mizan.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import iq.mizan.market.service.MarketService;
import iq.mizan.support.ApiClient;
import iq.mizan.support.PostgresIntegrationTest;
import iq.mizan.support.TestAccounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** ADR admin-1: operators run the service, every change needs the password again, and secrets stay sealed. */
class AdminIntegrationTest extends PostgresIntegrationTest {

    private static final String IQWEALTH_KEY = "iqw_test_secret_value_123";

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketService marketService;

    private String operator;
    private ApiClient.Session other;
    private UUID otherId;

    @BeforeEach
    void operatorAndAnotherAccount() {
        resetTransactionalData();
        jdbc.execute("delete from platform_setting; delete from announcement");
        operator = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        jdbc.update("update app_user set platform_role = 'OPERATOR' where email = ?", accounts.ownerEmail());
        other = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        otherId = UUID.fromString(api.body(api.get("/api/v1/me", other.accessToken())).get("id").asString());
    }

    private void elevate() {
        assertThat(api.post("/api/v1/admin/session", operator, Map.of("authKey", ApiClient.authKey(accounts.ownerPassword())))).hasStatus(HttpStatus.OK);
    }

    @Test
    void onlyOperatorsGetInAndChangesNeedThePasswordAgain() {
        assertThat(api.get("/api/v1/admin/overview", other.accessToken())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.body(api.get("/api/v1/me", operator)).get("operator").asBoolean()).isTrue();
        assertThat(api.body(api.get("/api/v1/me", other.accessToken())).get("operator").asBoolean()).isFalse();

        JsonNode overview = api.body(api.get("/api/v1/admin/overview", operator));
        assertThat(overview.get("accounts").get("total").asLong()).isEqualTo(2);
        assertThat(overview.get("accounts").get("operators").asLong()).isEqualTo(1);

        MvcTestResult unelevated = api.put("/api/v1/admin/settings/MARKET_MANUAL_DOLLAR_IQD", operator, Map.of("value", "1500"));
        assertThat(unelevated).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.body(unelevated).get("code").asString()).isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(api.post("/api/v1/admin/session", operator, Map.of("authKey", ApiClient.authKey("wrong-password")))).hasStatus(HttpStatus.UNAUTHORIZED);

        elevate();
        assertThat(api.body(api.get("/api/v1/admin/session", operator)).get("elevatedUntil").isNull()).isFalse();
        assertThat(api.put("/api/v1/admin/settings/MARKET_MANUAL_DOLLAR_IQD", operator, Map.of("value", "1500"))).hasStatus(HttpStatus.OK);
    }

    @Test
    void aSettingTakesEffectWithoutARestartAndSecretsAreSealedAndMasked() {
        elevate();
        api.put("/api/v1/admin/settings/MARKET_MANUAL_DOLLAR_IQD", operator, Map.of("value", "1500"));
        marketService.refresh();
        JsonNode dollar = null;
        for (JsonNode quote : api.body(api.get("/api/v1/market/quotes", operator))) {
            if ("USDIQD_PARALLEL".equals(quote.get("instrument").asString())) dollar = quote;
        }
        assertThat(dollar.get("priceMicros").asLong()).isEqualTo(1_500_000_000L);
        assertThat(dollar.get("source").asString()).isEqualTo("configured");

        assertThat(api.put("/api/v1/admin/settings/MARKET_IQWEALTH_API_KEY", operator, Map.of("value", IQWEALTH_KEY))).hasStatus(HttpStatus.OK);
        Map<String, Object> row = jdbc.queryForMap("select text_value, secret_value from platform_setting where setting_key = 'MARKET_IQWEALTH_API_KEY'");
        assertThat(row.get("text_value")).isNull();
        assertThat(new String((byte[]) row.get("secret_value"))).doesNotContain(IQWEALTH_KEY);

        JsonNode settings = api.body(api.get("/api/v1/admin/settings", operator));
        for (JsonNode setting : settings) {
            if ("MARKET_IQWEALTH_API_KEY".equals(setting.get("key").asString())) {
                assertThat(setting.get("value").asString()).isEqualTo("iqw_…123").doesNotContain("secret");
                assertThat(setting.get("overridden").asBoolean()).isTrue();
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where after_state::text like ?", Long.class, "%" + IQWEALTH_KEY + "%")).isZero();

        assertThat(api.put("/api/v1/admin/settings/MARKET_IQWEALTH_RESERVE", operator, Map.of("value", "500"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.put("/api/v1/admin/settings/MARKET_IQWEALTH_PARALLEL_SIDE", operator, Map.of("value", "MAYBE"))).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(api.delete("/api/v1/admin/settings/MARKET_MANUAL_DOLLAR_IQD", operator)).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select count(*) from platform_setting where setting_key = 'MARKET_MANUAL_DOLLAR_IQD'", Long.class)).isZero();
    }

    @Test
    void aSuspendedAccountIsSignedOutAndCannotSignInUntilRestored() {
        elevate();
        assertThat(api.body(api.get("/api/v1/admin/accounts?query=" + accounts.otherEmail().substring(0, 4), operator)).toString()).contains(otherId.toString());
        assertThat(api.post("/api/v1/admin/accounts/" + otherId + "/suspend", operator, Map.of("reason", "Reported abuse"))).hasStatus(HttpStatus.OK);

        assertThat(api.post("/api/v1/auth/refresh", null, Map.of("refreshToken", other.refreshToken()))).hasStatus(HttpStatus.UNAUTHORIZED);
        MvcTestResult login = api.login(accounts.otherEmail(), accounts.otherPassword());
        assertThat(login).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.body(login).get("code").asString()).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(api.login(accounts.otherEmail(), "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);

        api.post("/api/v1/admin/accounts/" + otherId + "/restore", operator, null);
        assertThat(api.login(accounts.otherEmail(), accounts.otherPassword())).hasStatus(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where action = 'ACCOUNT_SUSPENDED' and after_state->>'reason' = 'Reported abuse'", Long.class)).isEqualTo(1);
    }

    @Test
    void announcementsReachEveryoneUntilRetracted() {
        elevate();
        JsonNode published = api.body(api.post("/api/v1/admin/announcements", operator,
                Map.of("messageEn", "Maintenance tonight at 23:00", "messageAr", "صيانة الليلة الساعة 23:00", "level", "WARNING")));
        JsonNode seen = api.body(api.get("/api/v1/announcements", other.accessToken()));
        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).get("messageAr").asString()).isEqualTo("صيانة الليلة الساعة 23:00");

        api.delete("/api/v1/admin/announcements/" + published.get("id").asString(), operator);
        assertThat(api.body(api.get("/api/v1/announcements", other.accessToken()))).isEmpty();
        assertThat(api.post("/api/v1/admin/announcements", other.accessToken(), Map.of("messageEn", "x", "messageAr", "x", "level", "INFO"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void operatorsAreGrantedByOperatorsAndTheLastOneStays() {
        elevate();
        UUID me = UUID.fromString(api.body(api.get("/api/v1/me", operator)).get("id").asString());
        assertThat(api.delete("/api/v1/admin/operators/" + me, operator)).hasStatus(HttpStatus.CONFLICT);

        assertThat(api.body(api.post("/api/v1/admin/operators", operator, Map.of("identifier", accounts.otherEmail())))).hasSize(2);
        assertThat(api.get("/api/v1/admin/overview", other.accessToken())).hasStatus(HttpStatus.OK);
        api.delete("/api/v1/admin/operators/" + otherId, operator);
        assertThat(api.get("/api/v1/admin/overview", other.accessToken())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void aWrongPriceIsCorrectedOrRemovedWithAReason() {
        elevate();
        assertThat(api.post("/api/v1/admin/prices/corrections", operator,
                Map.of("instrument", "USDIQD_PARALLEL", "priceMicros", 1_712_000_000L, "reason", "Feed showed 17,120"))).hasStatus(HttpStatus.CREATED);
        JsonNode changes = api.body(api.get("/api/v1/market/changes?instrument=USDIQD_PARALLEL", operator)).get("changes");
        assertThat(changes.get(0).get("source").asString()).isEqualTo("correction");

        long id = changes.get(0).get("id").asLong();
        assertThat(api.delete("/api/v1/admin/prices/changes/" + id, operator, Map.of("reason", "Entered by mistake"))).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("select count(*) from price_tick where id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_entry where action = 'PRICE_TICK_DELETED'", Long.class)).isEqualTo(1);
    }

    @Test
    void theAuditLogCanBeSearchedByAction() {
        elevate();
        api.put("/api/v1/admin/settings/MARKET_REFRESH_COOLDOWN", operator, Map.of("value", "45s"));
        JsonNode entries = api.body(api.get("/api/v1/admin/audit?action=SETTING_CHANGED", operator));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("after").asString()).contains("MARKET_REFRESH_COOLDOWN").contains("45s");
        assertThat(api.get("/api/v1/admin/audit?action=NOT_A_THING", operator)).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
