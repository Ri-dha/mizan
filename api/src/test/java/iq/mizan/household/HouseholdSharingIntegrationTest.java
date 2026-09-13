package iq.mizan.household;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.household.service.HouseholdService;
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

/** FR-ACC-05..07 and BR-16: members share the household, private rows stay with their owner, roles gate writes. */
class HouseholdSharingIntegrationTest extends PostgresIntegrationTest {

    private static final String CLOCK = "1700000000000:0000:test";
    private static final long SHARED_WALLET = 1_000_000;
    private static final long PRIVATE_GOLD = 547_939;

    @Autowired
    private ApiClient api;

    @Autowired
    private TestAccounts accounts;

    @Autowired
    private MarketService marketService;

    @Autowired
    private HouseholdService householdService;

    @Autowired
    private JdbcTemplate jdbc;

    private String owner;
    private String ownerDevice;
    private ApiClient.Session otherSession;
    private String other;
    private UUID otherId;

    @BeforeEach
    void twoAccounts() {
        resetTransactionalData();
        marketService.refresh();
        owner = api.register(accounts.ownerEmail(), accounts.ownerPassword(), "Ridha").accessToken();
        ownerDevice = device(owner);
        otherSession = api.register(accounts.otherEmail(), accounts.otherPassword(), "Noor");
        other = otherSession.accessToken();
        otherId = UUID.fromString(api.body(api.get("/api/v1/me", other)).get("id").asString());
        push(owner, ownerDevice,
                op("cash_account", UUID.randomUUID(), fields("name", "Wallet", "kind", "WALLET", "currency", "IQD", "balance", SHARED_WALLET)),
                op("metal_lot", UUID.randomUUID(), fields("visibility", "PRIVATE", "metal", "GOLD", "purityLabel", "21k", "purityBasisPoints", 8750,
                        "weightMg", 5000, "weightUnitEntered", "MITHQAL", "quantityEntered", "1", "purchaseDate", "2026-06-01",
                        "metalCost", 520_000, "makingCharge", 30_000, "fees", 0, "currency", "IQD", "fxRateMicros", 1_000_000, "form", "JEWELLERY")));
    }

    @Test
    void aMemberSeesSharedRowsButNotTheOwnersPrivateOnes() {
        String member = join(HouseholdRoleName.MEMBER);
        String memberDevice = device(member);

        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + memberDevice + "&since=0", member));
        List<String> tables = new ArrayList<>();
        pull.get("records").forEach(record -> tables.add(record.get("table").asString()));
        assertThat(tables).contains("cash_account", "plan", "bucket").doesNotContain("metal_lot");

        push(member, memberDevice, op("cash_account", UUID.randomUUID(), fields("name", "Noor's purse", "kind", "WALLET", "currency", "IQD", "balance", 5000)));
        JsonNode ownerPull = api.body(api.get("/api/v1/sync/pull?deviceId=" + ownerDevice + "&since=0", owner));
        List<String> names = new ArrayList<>();
        ownerPull.get("records").forEach(record -> {
            if ("cash_account".equals(record.get("table").asString())) {
                names.add(record.get("fields").get("name").asString());
            }
        });
        assertThat(names).contains("Wallet", "Noor's purse");
    }

    @Test
    void theHouseholdFigureCountsPrivateRowsWithoutItemisingThem() {
        String member = join(HouseholdRoleName.MEMBER);

        JsonNode figures = api.body(api.get("/api/v1/networth/current", member));
        assertThat(figures.get("totalAssets").asLong()).isEqualTo(SHARED_WALLET + PRIVATE_GOLD);

        api.body(api.post("/api/v1/months/2026-09/close", owner, null));
        JsonNode pull = api.body(api.get("/api/v1/sync/pull?deviceId=" + device(member) + "&since=0", member));
        boolean sawSnapshot = false;
        for (JsonNode record : pull.get("records")) {
            if ("net_worth_snapshot".equals(record.get("table").asString())) {
                sawSnapshot = true;
                assertThat(record.get("fields").get("totalAssets").asLong()).isEqualTo(SHARED_WALLET + PRIVATE_GOLD);
            }
            assertThat(record.get("table").asString()).isNotEqualTo("metal_lot");
        }
        assertThat(sawSnapshot).isTrue();
    }

    @Test
    void aViewerCanPullButNotPush() {
        String viewer = join(HouseholdRoleName.VIEWER);
        String viewerDevice = device(viewer);

        assertThat(api.get("/api/v1/sync/pull?deviceId=" + viewerDevice + "&since=0", viewer)).hasStatus(HttpStatus.OK);
        MvcTestResult push = api.post("/api/v1/sync/push", viewer, Map.of("deviceId", viewerDevice, "ops",
                List.of(op("cash_account", UUID.randomUUID(), fields("name", "Nope", "kind", "WALLET", "currency", "IQD")))));
        assertThat(push).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(api.post("/api/v1/households/current/invitations", viewer, Map.of("role", "MEMBER"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void invitationsExpireAndCanBeRevokedAndPreviewed() {
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", "MEMBER", "contact", "noor@example.test")));
        String token = invitation.get("token").asString();

        JsonNode preview = api.body(api.get("/api/v1/invitations/" + token, null));
        assertThat(preview.get("householdName").asString()).isEqualTo("Ridha");
        assertThat(preview.get("role").asString()).isEqualTo("MEMBER");
        assertThat(api.body(api.get("/api/v1/households/current/invitations", owner))).hasSize(1);

        jdbc.update("update household_invitation set expires_at = now() - interval '1 minute'");
        assertThat(api.post("/api/v1/invitations/" + token + "/accept", other, null)).hasStatus(HttpStatus.GONE);

        JsonNode fresh = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", "MEMBER")));
        assertThat(api.delete("/api/v1/households/current/invitations/" + fresh.get("id").asString(), owner)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.post("/api/v1/invitations/" + fresh.get("token").asString() + "/accept", other, null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(api.get("/api/v1/invitations/nonsense", null)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rolesChangeOwnershipTransfersAndMembersLeave() {
        String member = join(HouseholdRoleName.MEMBER);

        JsonNode members = api.body(api.get("/api/v1/households/current/members", owner));
        assertThat(members).hasSize(2);

        JsonNode changed = api.body(api.patch("/api/v1/households/current/members/" + otherId, owner, Map.of("role", "VIEWER")));
        assertThat(changed.get("role").asString()).isEqualTo("VIEWER");
        assertThat(api.patch("/api/v1/households/current/members/" + otherId, owner, Map.of("role", "OWNER"))).hasStatus(HttpStatus.BAD_REQUEST);

        JsonNode left = api.body(api.post("/api/v1/households/current/leave", member, null));
        assertThat(left.get("role").asString()).isEqualTo("OWNER");
        assertThat(left.get("name").asString()).isEqualTo("Noor");
        assertThat(api.body(api.get("/api/v1/households/current/members", owner))).hasSize(1);

        String rejoined = join(HouseholdRoleName.MEMBER);
        assertThat(api.post("/api/v1/households/current/transfer-ownership", owner, Map.of("userId", otherId.toString()))).hasStatus(HttpStatus.NO_CONTENT);
        for (JsonNode m : api.body(api.get("/api/v1/households/current/members", owner))) {
            String expected = m.get("userId").asString().equals(otherId.toString()) ? "OWNER" : "MEMBER";
            assertThat(m.get("role").asString()).isEqualTo(expected);
        }
        assertThat(api.post("/api/v1/households/current/leave", rejoined, null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(api.post("/api/v1/households/current/leave", owner, null)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void joiningSwitchesTheCurrentHouseholdAndSwitchingBackIsExplicit() {
        String member = join(HouseholdRoleName.MEMBER);
        JsonNode memberships = api.body(api.get("/api/v1/households", member));
        assertThat(memberships).hasSize(2);
        UUID own = null;
        for (JsonNode m : memberships) {
            if ("OWNER".equals(m.get("role").asString())) {
                own = UUID.fromString(m.get("householdId").asString());
                assertThat(m.get("current").asBoolean()).isFalse();
            } else {
                assertThat(m.get("current").asBoolean()).isTrue();
            }
        }
        JsonNode switched = api.body(api.post("/api/v1/households/" + own + "/switch", member, null));
        assertThat(switched.get("name").asString()).isEqualTo("Noor");
        assertThat(api.body(api.get("/api/v1/me", refreshed())).get("household").get("name").asString()).isEqualTo("Noor");
    }

    @Test
    void householdDeletionNeedsThePasswordWaitsForTheGracePeriodAndFallsMembersBack() {
        String member = join(HouseholdRoleName.MEMBER);
        assertThat(api.post("/api/v1/households/current/deletion-request", owner, Map.of("password", "wrong"))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.post("/api/v1/households/current/deletion-request", member, Map.of("password", accounts.otherPassword()))).hasStatus(HttpStatus.FORBIDDEN);

        JsonNode requested = api.body(api.post("/api/v1/households/current/deletion-request", owner, Map.of("password", accounts.ownerPassword())));
        assertThat(requested.get("deletionRequestedAt").isNull()).isFalse();
        assertThat(api.post("/api/v1/households/current/invitations", owner, Map.of("role", "MEMBER"))).hasStatus(HttpStatus.CONFLICT);

        assertThat(householdService.purgeExpired()).isZero();
        jdbc.update("update household set deletion_requested_at = now() - interval '30 days' where deletion_requested_at is not null");
        assertThat(householdService.purgeExpired()).isEqualTo(1);

        assertThat(api.body(api.get("/api/v1/me", refreshed())).get("household").get("name").asString()).isEqualTo("Noor");
        assertThat(api.get("/api/v1/me", owner)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private enum HouseholdRoleName { MEMBER, VIEWER }

    private String join(HouseholdRoleName role) {
        JsonNode invitation = api.body(api.post("/api/v1/households/current/invitations", owner, Map.of("role", role.name())));
        JsonNode joined = api.body(api.post("/api/v1/invitations/" + invitation.get("token").asString() + "/accept", other, null));
        assertThat(joined.get("name").asString()).isEqualTo("Ridha");
        return refreshed();
    }

    /** Tokens carry the household, so a join or switch takes effect on the next refresh. */
    private String refreshed() {
        otherSession = api.refresh(otherSession.refreshToken());
        other = otherSession.accessToken();
        return other;
    }

    private String device(String token) {
        return api.body(api.post("/api/v1/sync/devices", token, Map.of("name", "Phone"))).get("id").asString();
    }

    private void push(String token, String deviceId, Map<String, Object>... ops) {
        assertThat(api.post("/api/v1/sync/push", token, Map.of("deviceId", deviceId, "ops", List.of(ops)))).hasStatus(HttpStatus.OK);
    }

    private static Map<String, Object> fields(Object... keysAndValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> op(String table, UUID rowId, Map<String, Object> fields) {
        Map<String, String> clocks = new java.util.HashMap<>();
        fields.keySet().forEach(field -> clocks.put(field, CLOCK));
        return Map.of("opId", UUID.randomUUID().toString(), "table", table, "rowId", rowId.toString(), "fields", fields, "clocks", clocks);
    }
}
