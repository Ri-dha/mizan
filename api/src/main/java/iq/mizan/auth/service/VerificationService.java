package iq.mizan.auth.service;

import java.util.UUID;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.entity.CodePurpose;
import iq.mizan.auth.entity.VerificationCode;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.auth.repository.VerificationCodeRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.HashingService;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and checks one-time codes for contact verification and password reset. */
@Service
@AllArgsConstructor
public class VerificationService {

    private final AppUserRepository userRepository;
    private final VerificationCodeRepository codeRepository;
    private final HashingService hashingService;
    private final CodeGenerator codeGenerator;
    private final CodeSender codeSender;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Transactional
    public void sendContactVerification(AppUser user) {
        if (user.isVerified()) {
            throw ApiException.of(ErrorCode.ALREADY_VERIFIED, "This account is already verified");
        }
        issue(user, CodePurpose.VERIFY_CONTACT, authProperties.verification().ttl());
    }

    @Transactional
    public void resendContactVerification(UUID userId) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        if (user.isVerified()) {
            throw ApiException.of(ErrorCode.ALREADY_VERIFIED, "This account is already verified");
        }
        refuseIfResentTooSoon(user.getId(), CodePurpose.VERIFY_CONTACT);
        issue(user, CodePurpose.VERIFY_CONTACT, authProperties.verification().ttl());
    }

    @Transactional
    public void confirmContact(UUID userId, String code, String ipAddress) {
        AppUser user = userRepository.findById(userId).orElseThrow();
        consume(user.getId(), CodePurpose.VERIFY_CONTACT, code);
        user.markVerified();
        auditService.record(AuditRecord.of(AuditAction.CONTACT_VERIFIED, AuditEntityType.USER)
                .actorId(user.getId())
                .entityId(user.getId())
                .ipAddress(ipAddress)
                .build());
    }

    @Transactional
    public void sendPasswordReset(AppUser user) {
        refuseIfResentTooSoon(user.getId(), CodePurpose.PASSWORD_RESET);
        issue(user, CodePurpose.PASSWORD_RESET, authProperties.passwordReset().ttl());
    }

    @Transactional
    public void consumePasswordReset(UUID userId, String code) {
        consume(userId, CodePurpose.PASSWORD_RESET, code);
    }

    private void issue(AppUser user, CodePurpose purpose, java.time.Duration ttl) {
        String code = codeGenerator.numeric(authProperties.verification().codeLength());
        codeRepository.save(VerificationCode.issue(
                user.getId(), purpose, hashingService.hash(code), ttl));
        codeSender.send(user.contact(), purpose, code);
    }

    private void refuseIfResentTooSoon(UUID userId, CodePurpose purpose) {
        codeRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .filter(latest -> latest.isIssuedWithin(authProperties.verification().resendCooldown()))
                .ifPresent(latest -> {
                    throw ApiException.of(ErrorCode.VERIFICATION_RESEND_TOO_SOON,
                            "Please wait before requesting another code");
                });
    }

    private void consume(UUID userId, CodePurpose purpose, String presented) {
        VerificationCode latest = codeRepository
                .findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .filter(VerificationCode::isUsable)
                .orElseThrow(VerificationService::invalidCode);

        if (!latest.hasAttemptsLeft(authProperties.verification().maxAttempts())) {
            throw ApiException.of(ErrorCode.VERIFICATION_ATTEMPTS_EXHAUSTED,
                    "Too many wrong codes. Request a new one.");
        }
        if (!hashingService.matches(presented, latest.getCodeHash())) {
            latest.recordFailedAttempt();
            throw invalidCode();
        }
        latest.consume();
    }

    private static ApiException invalidCode() {
        return ApiException.of(ErrorCode.INVALID_VERIFICATION_CODE, "The code is wrong or has expired");
    }
}
