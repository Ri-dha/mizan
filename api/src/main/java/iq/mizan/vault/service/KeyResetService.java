package iq.mizan.vault.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.household.dto.MembershipResponse;
import iq.mizan.household.service.HouseholdService;
import iq.mizan.vault.dto.ResetKeysRequest;
import iq.mizan.vault.dto.ResetPreviewResponse;
import iq.mizan.vault.entity.UserKey;
import iq.mizan.vault.repository.UserKeyRepository;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR e2e-3: starting over without a recovery code. Whatever was sealed under keys nobody can
 * open any more is removed, so it cannot break anyone's sync, and the person can be let back in.
 * Runs as the system because it reaches every household the person belongs to.
 */
@Service
@AllArgsConstructor
public class KeyResetService {

    static final String CONFIRMATION = "RESET";

    private final UserKeyRepository userKeys;
    private final HouseholdService households;
    private final TenantSession tenantSession;
    private final JdbcClient jdbc;
    private final AuditService auditService;
    private final Clock clock;

    private record Plan(long privateRecords, List<ResetPreviewResponse.Household> households) {
    }

    @Transactional(readOnly = true)
    public ResetPreviewResponse preview(UUID userId) {
        Plan plan = plan(userId);
        return new ResetPreviewResponse(plan.privateRecords(), plan.households());
    }

    @Transactional
    public ResetPreviewResponse reset(UUID userId, ResetKeysRequest request) {
        if (!CONFIRMATION.equals(request.confirm())) {
            throw ApiException.of(ErrorCode.RESET_NOT_CONFIRMED, "Type RESET to confirm that your old records will be removed");
        }
        UserKey key = userKeys.findById(userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ACCOUNT_KEYS_MISSING, "This account has no keys yet"));
        Plan plan = plan(userId);

        key.replace(request.wrappedByPassword(), request.wrappedByRecovery(), request.publicKey(), request.encryptedPrivateKey(), clock.instant());
        long privateDeleted = jdbc.sql("delete from vault_record where owner_id = ? and visibility = 'PRIVATE'").param(userId).update();
        long grantsDeleted = jdbc.sql("delete from household_key_grant where user_id = ?").param(userId).update();
        long sharedDeleted = 0;
        for (ResetPreviewResponse.Household household : plan.households()) {
            if (!household.soleKeyHolder()) {
                continue;
            }
            sharedDeleted += jdbc.sql("delete from vault_record where household_id = ? and visibility = 'SHARED'")
                    .param(household.householdId()).update();
            grantsDeleted += jdbc.sql("delete from household_key_grant where household_id = ?").param(household.householdId()).update();
            jdbc.sql("delete from household_key_state where household_id = ?").param(household.householdId()).update();
        }

        auditService.record(AuditRecord.of(AuditAction.KEYS_RESET, AuditEntityType.USER).actorId(userId).entityId(userId)
                .after(Map.of("privateRecordsDeleted", privateDeleted, "sharedRecordsDeleted", sharedDeleted, "grantsDeleted", grantsDeleted,
                        "householdsRestarted", plan.households().stream().filter(ResetPreviewResponse.Household::soleKeyHolder).count()))
                .build());
        return new ResetPreviewResponse(privateDeleted, plan.households());
    }

    private Plan plan(UUID userId) {
        List<MembershipResponse> memberships = households.memberships(userId);
        tenantSession.elevateToSystem();
        long privateRecords = jdbc.sql("select count(*) from vault_record where owner_id = ? and visibility = 'PRIVATE'")
                .param(userId).query(Long.class).single();
        List<ResetPreviewResponse.Household> affected = new ArrayList<>();
        for (MembershipResponse membership : memberships) {
            UUID householdId = membership.householdId();
            boolean holdsKey = holdsHouseholdKey(householdId, userId, true);
            boolean othersHoldKey = holdsHouseholdKey(householdId, userId, false);
            // Only someone holding the key can be its last holder; a dependent's reset never touches shared rows.
            boolean sole = holdsKey && !othersHoldKey;
            long sharedLost = !sole ? 0 : jdbc.sql("select count(*) from vault_record where household_id = ? and visibility = 'SHARED'")
                    .param(householdId).query(Long.class).single();
            affected.add(new ResetPreviewResponse.Household(householdId, membership.householdName(), sole, sharedLost));
        }
        return new Plan(privateRecords, affected);
    }

    private boolean holdsHouseholdKey(UUID householdId, UUID userId, boolean self) {
        return jdbc.sql("select exists (select 1 from household_key_grant where household_id = ? and key_kind = 'HOUSEHOLD' and "
                        + (self ? "user_id = ?" : "user_id <> ?") + ")")
                .param(householdId).param(userId).query(Boolean.class).single();
    }
}
