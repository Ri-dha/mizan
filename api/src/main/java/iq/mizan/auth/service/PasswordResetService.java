package iq.mizan.auth.service;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;

import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class PasswordResetService {

    private final UserLookup userLookup;
    private final VerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessionRevoker;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    /** Always succeeds from the caller's view, so it cannot be used to probe for accounts. */
    @Transactional
    public void request(String rawIdentifier) {
        Identifier identifier = Identifier.parse(rawIdentifier, authProperties.defaultCountryCode());
        userLookup.byIdentifier(identifier)
                .filter(AppUser::isActive)
                .ifPresent(verificationService::sendPasswordReset);
    }

    @Transactional
    public void confirm(String rawIdentifier, String code, String newPassword, String ipAddress) {
        Identifier identifier = Identifier.parse(rawIdentifier, authProperties.defaultCountryCode());
        AppUser user = userLookup.byIdentifier(identifier)
                .filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.of(
                        ErrorCode.INVALID_VERIFICATION_CODE, "The code is wrong or has expired"));

        verificationService.consumePasswordReset(user.getId(), code);
        user.changePassword(passwordEncoder.encode(newPassword));
        sessionRevoker.revokeAllSessions(user.getId());

        auditService.record(AuditRecord.of(AuditAction.PASSWORD_RESET, AuditEntityType.USER)
                .actorId(user.getId())
                .entityId(user.getId())
                .ipAddress(ipAddress)
                .build());
    }
}
