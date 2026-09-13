package iq.mizan.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-ACC-08: request, cancel within the grace window, and the purge that follows it. */
@Service
@AllArgsConstructor
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final AppUserRepository userRepository;
    private final SessionRevoker sessionRevoker;
    private final AuditService auditService;
    private final JdbcClient jdbc;
    private final AuthProperties properties;
    private final Clock clock;

    @Transactional
    public Instant request(UUID userId, String ipAddress) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        user.requestDeletion();
        audit(AuditAction.DELETION_REQUESTED, user, ipAddress);
        return user.getDeletionRequestedAt();
    }

    @Transactional
    public void cancel(UUID userId, String ipAddress) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        user.cancelDeletion();
        audit(AuditAction.DELETION_CANCELLED, user, ipAddress);
    }

    /**
     * Households go first: their tables cascade from the household row, and foreign keys
     * bypass row-level security, so nothing is left behind for the user delete to trip on.
     */
    @Transactional
    public int purgeExpired() {
        Instant cutoff = clock.instant().minus(properties.deletion().gracePeriod());
        List<Map<String, Object>> due = jdbc.sql("select u.id as user_id, m.household_id from app_user u "
                        + "join membership m on m.user_id = u.id and m.role = 'OWNER' "
                        + "where u.deletion_requested_at is not null and u.deletion_requested_at < ?")
                .param(java.sql.Timestamp.from(cutoff)).query().listOfRows();
        for (Map<String, Object> row : due) {
            UUID userId = (UUID) row.get("user_id");
            sessionRevoker.revokeAllSessions(userId);
            jdbc.sql("delete from household where id = ?").param(row.get("household_id")).update();
            jdbc.sql("delete from app_user where id = ?").param(userId).update();
            auditService.record(AuditRecord.of(AuditAction.ACCOUNT_PURGED, AuditEntityType.USER)
                    .entityId(userId).householdId((UUID) row.get("household_id")).build());
            log.info("Purged account {} after its grace period", userId);
        }
        return due.size();
    }

    private void audit(AuditAction action, AppUser user, String ipAddress) {
        auditService.record(AuditRecord.of(action, AuditEntityType.USER)
                .actorId(user.getId()).entityId(user.getId()).ipAddress(ipAddress).build());
    }
}
