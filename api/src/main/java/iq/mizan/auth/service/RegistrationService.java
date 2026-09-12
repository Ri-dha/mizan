package iq.mizan.auth.service;

import iq.mizan.auth.AuthProperties;
import iq.mizan.auth.entity.AppUser;
import iq.mizan.auth.repository.AppUserRepository;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.household.service.HouseholdService;
import iq.mizan.household.service.HouseholdSummary;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class RegistrationService {

    private final AppUserRepository userRepository;
    private final UserLookup userLookup;
    private final PasswordEncoder passwordEncoder;
    private final HouseholdService householdService;
    private final VerificationService verificationService;
    private final TokenIssuer tokenIssuer;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Transactional
    public TokenPair register(
            String rawIdentifier, String rawPassword, String displayName, String locale,
            String userAgent, String ipAddress) {

        Identifier identifier = Identifier.parse(rawIdentifier, authProperties.defaultCountryCode());
        if (userLookup.byIdentifier(identifier).isPresent()) {
            throw identifierAlreadyRegistered();
        }

        AppUser user;
        try {
            user = userRepository.saveAndFlush(AppUser.register(
                    identifier, displayName, passwordEncoder.encode(rawPassword), locale));
        } catch (DataIntegrityViolationException e) {
            throw identifierAlreadyRegistered();
        }

        HouseholdSummary household = householdService.createForOwner(user.getId(), displayName);
        verificationService.sendContactVerification(user);

        auditService.record(AuditRecord.of(AuditAction.REGISTERED, AuditEntityType.USER)
                .actorId(user.getId())
                .entityId(user.getId())
                .householdId(household.id())
                .ipAddress(ipAddress)
                .build());

        return tokenIssuer.issue(user, household, userAgent, ipAddress);
    }

    private static ApiException identifierAlreadyRegistered() {
        return ApiException.of(ErrorCode.IDENTIFIER_ALREADY_REGISTERED,
                "An account with this email or phone already exists");
    }
}
