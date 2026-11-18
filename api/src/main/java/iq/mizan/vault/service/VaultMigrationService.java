package iq.mizan.vault.service;

import java.util.Map;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.security.Permission;
import iq.mizan.common.tenancy.TenantSession;
import iq.mizan.household.service.HouseholdService;
import iq.mizan.sync.table.SyncTable;
import iq.mizan.sync.table.SyncTableRegistry;
import iq.mizan.vault.dto.VaultStatusResponse;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The end of the plain era for a household (ADR e2e-1). A device that has re-uploaded rows as
 * ciphertext tells the server to delete their plain copies: its owner's private rows always,
 * the shared rows too when the caller owns the household. Members' private rows wait for their
 * own devices, because nobody else can read them to encrypt them.
 */
@Service
@AllArgsConstructor
public class VaultMigrationService {

    private final SyncTableRegistry registry;
    private final HouseholdService households;
    private final TenantSession tenantSession;
    private final AuditService auditService;
    private final JdbcClient jdbc;

    @Transactional
    public VaultStatusResponse status(CurrentUser user) {
        tenantSession.elevateToSystem();
        return new VaultStatusResponse(households.isVaultMigrated(user.householdId()), legacyRows(user));
    }

    @Transactional
    public VaultStatusResponse complete(CurrentUser user) {
        tenantSession.elevateToSystem();
        boolean owner = user.permissions().contains(Permission.HOUSEHOLD_DELETE);
        long deleted = 0;
        for (SyncTable table : registry.all()) {
            if (!legacyTableExists(table)) {
                continue;
            }
            deleted += jdbc.sql("delete from " + table.name() + " where household_id = ? and (owner_id = ? or (? and visibility = 'SHARED'))")
                    .param(user.householdId()).param(user.userId()).param(owner).update();
        }
        long remaining = legacyRows(user);
        if (remaining == 0) {
            households.markVaultMigrated(user.householdId());
        }
        auditService.record(AuditRecord.of(AuditAction.VAULT_MIGRATED, AuditEntityType.HOUSEHOLD)
                .actorId(user.userId()).householdId(user.householdId()).entityId(user.householdId())
                .after(Map.of("deletedPlainRows", deleted, "remaining", remaining)).build());
        return new VaultStatusResponse(remaining == 0, remaining);
    }

    private long legacyRows(CurrentUser user) {
        long total = 0;
        for (SyncTable table : registry.all()) {
            if (legacyTableExists(table)) {
                total += jdbc.sql("select count(*) from " + table.name() + " where household_id = ?")
                        .param(user.householdId()).query(Long.class).single();
            }
        }
        return total;
    }

    /** Record types added after ADR e2e-1 (annual reports, contributions) never had a plain table. */
    private boolean legacyTableExists(SyncTable table) {
        return jdbc.sql("select to_regclass(?) is not null").param("public." + table.name()).query(Boolean.class).single();
    }
}
