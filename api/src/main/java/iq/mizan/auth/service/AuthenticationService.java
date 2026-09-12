package iq.mizan.auth.service;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.entity.RefreshToken;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.auth.repository.RefreshTokenRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.HashingService;
import iq.mizan.household.service.HouseholdService;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AppUserRepository userRepository;
    private final UserLookup userLookup;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final HashingService hashingService;
    private final HouseholdService householdService;
    private final TokenIssuer tokenIssuer;
    private final SessionRevoker sessionRevoker;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final DummyPasswordHash dummyPasswordHash;

    @Transactional
    public TokenPair login(String rawIdentifier, String rawPassword, String userAgent, String ipAddress) {
        Identifier identifier = Identifier.parse(rawIdentifier, authProperties.defaultCountryCode());
        AppUser user = userLookup.byIdentifier(identifier).orElse(null);

        if (user == null) {
            // Costs the same as a wrong password, so timing cannot enumerate accounts.
            passwordEncoder.matches(rawPassword, dummyPasswordHash.value());
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw ApiException.of(ErrorCode.ACCOUNT_NOT_ACTIVE, "This account is not active");
        }
        if (user.isLocked()) {
            throw ApiException.of(ErrorCode.ACCOUNT_LOCKED, "Too many failed attempts. Try again later.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            loginAttemptRecorder.recordFailure(user.getId(), ipAddress);
            throw invalidCredentials();
        }

        user.recordSuccessfulLogin();
        audit(AuditAction.LOGIN_SUCCEEDED, user, ipAddress);
        return tokenIssuer.issue(user, householdService.summaryFor(user.getId()), userAgent, ipAddress);
    }

    @Transactional
    public TokenPair refresh(String presentedToken, String userAgent, String ipAddress) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(hashingService.hash(presentedToken))
                .orElseThrow(AuthenticationService::invalidRefreshToken);

        if (!stored.isActive()) {
            // A rotated-out token being replayed is the signature of theft: end every session.
            sessionRevoker.revokeAllSessions(stored.getUserId());
            auditService.recordNow(AuditRecord.of(AuditAction.TOKEN_REUSE_DETECTED, AuditEntityType.USER)
                    .actorId(stored.getUserId())
                    .entityId(stored.getUserId())
                    .ipAddress(ipAddress)
                    .build());
            log.warn("Refresh token reuse detected for user {}", stored.getUserId());
            throw invalidRefreshToken();
        }

        AppUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(AuthenticationService::invalidRefreshToken);
        if (!user.isActive()) {
            throw ApiException.of(ErrorCode.ACCOUNT_NOT_ACTIVE, "This account is not active");
        }

        TokenPair pair = tokenIssuer.issue(user, householdService.summaryFor(user.getId()), userAgent, ipAddress);
        refreshTokenRepository.findByTokenHash(hashingService.hash(pair.refreshToken()))
                .ifPresent(stored::rotateTo);
        audit(AuditAction.TOKEN_REFRESHED, user, ipAddress);
        return pair;
    }

    /** Unknown tokens succeed silently: the end state is the same. */
    @Transactional
    public void logout(String presentedToken) {
        refreshTokenRepository.findByTokenHash(hashingService.hash(presentedToken))
                .ifPresent(token -> {
                    token.revoke();
                    auditService.record(AuditRecord.of(AuditAction.LOGOUT, AuditEntityType.USER)
                            .actorId(token.getUserId())
                            .entityId(token.getUserId())
                            .build());
                });
    }

    private void audit(AuditAction action, AppUser user, String ipAddress) {
        auditService.record(AuditRecord.of(action, AuditEntityType.USER)
                .actorId(user.getId())
                .entityId(user.getId())
                .ipAddress(ipAddress)
                .build());
    }

    private static ApiException invalidCredentials() {
        return ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Email, phone or password is incorrect");
    }

    private static ApiException invalidRefreshToken() {
        return ApiException.of(ErrorCode.INVALID_REFRESH_TOKEN, "The session has expired. Please sign in again.");
    }
}
