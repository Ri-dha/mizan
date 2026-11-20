package iq.mizan.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What an operator may do for a person who asks for help (ADR admin-1): find the account and
 * change its state. Nothing here reads, exports or decrypts any household's records.
 */
@Service
@AllArgsConstructor
public class AccountSupportService {

    private static final int MIN_QUERY = 3;

    public record AccountSummary(UUID id, String displayName, String email, String phone, Instant createdAt, boolean verified,
                                 String status, String authScheme, boolean operator, Instant suspendedAt, Instant lockedUntil,
                                 Instant deletionRequestedAt) {
    }

    private final AppUserRepository users;
    private final SessionRevoker sessionRevoker;
    private final AccountDeletionService deletions;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AccountSummary> search(String query) {
        String text = query == null ? "" : query.trim();
        if (text.length() < MIN_QUERY) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Type at least %d characters".formatted(MIN_QUERY));
        }
        return users.findTop20ByEmailStartingWithIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(text, text)
                .stream().map(AccountSupportService::summary).toList();
    }

    @Transactional
    public AccountSummary suspend(UUID userId, UUID actor, String reason) {
        AppUser user = user(userId);
        if (user.getId().equals(actor)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "You cannot suspend your own account");
        }
        user.suspend(clock.instant());
        sessionRevoker.revokeAllSessions(userId);
        auditService.record(AuditRecord.of(AuditAction.ACCOUNT_SUSPENDED, AuditEntityType.USER).actorId(actor).entityId(userId)
                .after(Map.of("reason", reason)).build());
        return summary(user);
    }

    @Transactional
    public AccountSummary restore(UUID userId, UUID actor) {
        AppUser user = user(userId);
        user.restore();
        auditService.record(AuditRecord.of(AuditAction.ACCOUNT_RESTORED, AuditEntityType.USER).actorId(actor).entityId(userId).build());
        return summary(user);
    }

    @Transactional
    public void revokeSessions(UUID userId, UUID actor) {
        user(userId);
        sessionRevoker.revokeAllSessions(userId);
        auditService.record(AuditRecord.of(AuditAction.SESSIONS_REVOKED, AuditEntityType.USER).actorId(actor).entityId(userId).build());
    }

    @Transactional
    public AccountSummary cancelDeletion(UUID userId, UUID actor, String ipAddress) {
        deletions.cancel(userId, ipAddress);
        auditService.record(AuditRecord.of(AuditAction.DELETION_CANCELLED, AuditEntityType.USER).actorId(actor).entityId(userId)
                .after(Map.of("by", "operator")).build());
        return summary(user(userId));
    }

    private AppUser user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "No such account"));
    }

    private static AccountSummary summary(AppUser u) {
        return new AccountSummary(u.getId(), u.getDisplayName(), u.getEmail(), u.getPhone(), u.getCreatedAt(), u.isVerified(),
                u.getAccountStatus().name(), u.getAuthScheme().name(), u.isOperator(), u.getSuspendedAt(),
                u.isLocked() ? u.getLockedUntil() : null, u.getDeletionRequestedAt());
    }
}
