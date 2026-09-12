package iq.mizan.auth.service;

import java.util.UUID;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Its own transaction: the 401 the caller throws next must not roll the attempt count back. */
@Service
@AllArgsConstructor
public class LoginAttemptRecorder {

    private final AppUserRepository userRepository;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, String ipAddress) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        user.recordFailedLogin(authProperties.lockout().maxAttempts(), authProperties.lockout().duration());
        audit(AuditAction.LOGIN_FAILED, user, ipAddress);
        if (user.isLocked()) {
            audit(AuditAction.ACCOUNT_LOCKED, user, ipAddress);
        }
    }

    private void audit(AuditAction action, AppUser user, String ipAddress) {
        auditService.record(AuditRecord.of(action, AuditEntityType.USER)
                .actorId(user.getId())
                .entityId(user.getId())
                .ipAddress(ipAddress)
                .build());
    }
}
