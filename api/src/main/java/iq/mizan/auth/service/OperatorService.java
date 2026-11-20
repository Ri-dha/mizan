package iq.mizan.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.entity.PlatformRole;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.PasswordVerifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR admin-1: who runs the service. The role is read from the database on every admin request,
 * so revoking it takes effect at once; a change also needs the password again within
 * {@code mizan.admin.elevation}.
 */
@Service
public class OperatorService {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    public record OperatorSummary(UUID id, String displayName, String email, String phone, Instant elevatedUntil) {
    }

    private final AppUserRepository users;
    private final PasswordVerifier passwords;
    private final AuditService auditService;
    private final Duration elevation;
    private final String bootstrapIdentifier;
    private final Clock clock;

    public OperatorService(AppUserRepository users, PasswordVerifier passwords, AuditService auditService,
                           @Value("${mizan.admin.elevation:10m}") Duration elevation,
                           @Value("${mizan.admin.bootstrap-operator:}") String bootstrapIdentifier, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.auditService = auditService;
        this.elevation = elevation;
        this.bootstrapIdentifier = bootstrapIdentifier;
        this.clock = clock;
    }

    /** The first operator comes from the environment; every later one is granted by an operator. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (bootstrapIdentifier == null || bootstrapIdentifier.isBlank()) {
            return;
        }
        find(bootstrapIdentifier).ifPresentOrElse(user -> {
            if (!user.isOperator()) {
                user.grantOperator();
                auditService.record(AuditRecord.of(AuditAction.OPERATOR_GRANTED, AuditEntityType.USER).entityId(user.getId())
                        .after(Map.of("by", "MIZAN_BOOTSTRAP_OPERATOR")).build());
                log.info("Granted the operator role to the bootstrap account");
            }
        }, () -> log.warn("MIZAN_BOOTSTRAP_OPERATOR names no account yet; register it, then restart"));
    }

    @Transactional(readOnly = true)
    public boolean isOperator(UUID userId) {
        return users.findById(userId).map(AppUser::isOperator).orElse(false);
    }

    @Transactional(readOnly = true)
    public void requireOperator(UUID userId) {
        if (!isOperator(userId)) {
            throw ApiException.of(ErrorCode.OPERATOR_REQUIRED, "This needs the operator role");
        }
    }

    @Transactional(readOnly = true)
    public void requireElevated(UUID userId) {
        requireOperator(userId);
        if (!user(userId).isElevatedAt(clock.instant())) {
            throw ApiException.of(ErrorCode.REAUTHENTICATION_REQUIRED, "Enter your password again to make changes");
        }
    }

    @Transactional(readOnly = true)
    public Instant elevatedUntil(UUID userId) {
        AppUser user = user(userId);
        return user.isElevatedAt(clock.instant()) ? user.getOperatorElevatedUntil() : null;
    }

    @Transactional
    public Instant elevate(UUID userId, String authKey, String ipAddress) {
        requireOperator(userId);
        if (!passwords.matches(userId, authKey)) {
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "That password is not right");
        }
        Instant until = clock.instant().plus(elevation);
        user(userId).elevateUntil(until);
        auditService.record(AuditRecord.of(AuditAction.OPERATOR_ELEVATED, AuditEntityType.USER).actorId(userId).entityId(userId)
                .ipAddress(ipAddress).build());
        return until;
    }

    @Transactional(readOnly = true)
    public List<OperatorSummary> operators() {
        Instant now = clock.instant();
        return users.findByPlatformRoleOrderByDisplayName(PlatformRole.OPERATOR).stream()
                .map(u -> new OperatorSummary(u.getId(), u.getDisplayName(), u.getEmail(), u.getPhone(),
                        u.isElevatedAt(now) ? u.getOperatorElevatedUntil() : null))
                .toList();
    }

    @Transactional
    public void grant(String identifier, UUID actor) {
        AppUser user = find(identifier).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "No account has that email"));
        if (user.isSuspended()) {
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED, "A suspended account cannot be an operator");
        }
        user.grantOperator();
        auditService.record(AuditRecord.of(AuditAction.OPERATOR_GRANTED, AuditEntityType.USER).actorId(actor).entityId(user.getId()).build());
    }

    @Transactional
    public void revoke(UUID userId, UUID actor) {
        AppUser user = user(userId);
        if (user.isOperator() && users.countByPlatformRole(PlatformRole.OPERATOR) <= 1) {
            throw ApiException.of(ErrorCode.LAST_OPERATOR, "Grant another operator before removing the last one");
        }
        user.revokeOperator();
        auditService.record(AuditRecord.of(AuditAction.OPERATOR_REVOKED, AuditEntityType.USER).actorId(actor).entityId(userId).build());
    }

    private java.util.Optional<AppUser> find(String rawIdentifier) {
        return users.findByEmailIgnoreCase(Identifier.parse(rawIdentifier).email());
    }

    private AppUser user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.of(ErrorCode.USER_NOT_FOUND, "No such account"));
    }
}
