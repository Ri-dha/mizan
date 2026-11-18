package iq.mizan.vault.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditEntityType;
import iq.mizan.common.audit.AuditRecord;
import iq.mizan.common.audit.AuditService;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.common.security.Permission;
import iq.mizan.household.service.HouseholdService;
import iq.mizan.household.service.MemberAccess;
import iq.mizan.vault.dto.AccountKeysResponse;
import iq.mizan.vault.dto.GrantRequest;
import iq.mizan.vault.dto.GrantResponse;
import iq.mizan.vault.dto.KeyMemberResponse;
import iq.mizan.vault.dto.MyKeysResponse;
import iq.mizan.vault.dto.RewrapRequest;
import iq.mizan.vault.entity.HouseholdKeyGrant;
import iq.mizan.vault.entity.KeyKind;
import iq.mizan.vault.entity.UserKey;
import iq.mizan.vault.repository.HouseholdKeyGrantRepository;
import iq.mizan.vault.repository.UserKeyRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The key directory (ADR e2e-1). It stores only wrapped keys and public keys, so nothing here lets
 * the server decrypt a record; its job is deciding who may hand a key to whom.
 */
@Service
@AllArgsConstructor
public class KeyService {

    private static final int FIRST_VERSION = 1;

    private final UserKeyRepository userKeys;
    private final HouseholdKeyGrantRepository grants;
    private final HouseholdService households;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public void storeAccountKeys(UUID userId, String wrappedByPassword, String wrappedByRecovery, String publicKey, String encryptedPrivateKey) {
        if (userKeys.existsById(userId)) {
            throw ApiException.of(ErrorCode.ACCOUNT_KEYS_EXIST, "This account already has keys");
        }
        userKeys.save(UserKey.of(userId, wrappedByPassword, wrappedByRecovery, publicKey, encryptedPrivateKey, clock.instant()));
        auditService.record(AuditRecord.of(AuditAction.KEYS_CREATED, AuditEntityType.USER).actorId(userId).entityId(userId).build());
    }

    @Transactional(readOnly = true)
    public MyKeysResponse mine(CurrentUser user) {
        AccountKeysResponse account = userKeys.findById(user.userId())
                .map(key -> new AccountKeysResponse(key.getWrappedByPassword(), key.getWrappedByRecovery(), key.getPublicKey(), key.getEncryptedPrivateKey()))
                .orElse(null);
        List<GrantResponse> held = grants.findByHouseholdIdAndUserIdOrderByKeyKindAscKeyVersionAsc(user.householdId(), user.userId()).stream()
                .map(grant -> new GrantResponse(grant.getKeyKind(), grant.getKeyVersion(), grant.getWrappedKey(), grant.getGrantedBy()))
                .toList();
        return new MyKeysResponse(account, held);
    }

    @Transactional
    public void rewrap(CurrentUser user, RewrapRequest request) {
        UserKey key = userKeys.findById(user.userId())
                .orElseThrow(() -> ApiException.of(ErrorCode.ACCOUNT_KEYS_MISSING, "This account has no keys yet"));
        if (request.wrappedByPassword() != null) {
            key.rewrapByPassword(request.wrappedByPassword(), clock.instant());
        }
        if (request.wrappedByRecovery() != null) {
            key.rewrapByRecovery(request.wrappedByRecovery(), clock.instant());
        }
        auditService.record(AuditRecord.of(AuditAction.KEYS_REWRAPPED, AuditEntityType.USER).actorId(user.userId()).entityId(user.userId())
                .after(Map.of("password", request.wrappedByPassword() != null, "recovery", request.wrappedByRecovery() != null)).build());
    }

    @Transactional(readOnly = true)
    public List<KeyMemberResponse> members(CurrentUser user) {
        return households.activeMembers(user.householdId()).stream()
                .map(member -> new KeyMemberResponse(member.userId(), member.displayName(), member.role(),
                        member.permissions().contains(Permission.HOUSEHOLD_VIEW),
                        member.permissions().contains(Permission.REPORTS_VIEW),
                        userKeys.findById(member.userId()).map(UserKey::getPublicKey).orElse(null),
                        grants.findByHouseholdIdAndUserIdOrderByKeyKindAscKeyVersionAsc(user.householdId(), member.userId()).stream()
                                .map(grant -> new KeyMemberResponse.HeldKey(grant.getKeyKind().name(), grant.getKeyVersion()))
                                .toList()))
                .toList();
    }

    /**
     * A member may pass on only a key they hold, to a member whose role may hold it. The one
     * exception starts a household: its owner creates version 1 when nobody holds that kind yet.
     */
    @Transactional
    public void grant(CurrentUser user, List<GrantRequest> requests) {
        UUID householdId = user.householdId();
        Map<UUID, MemberAccess> members = households.activeMembers(householdId).stream()
                .collect(Collectors.toMap(MemberAccess::userId, Function.identity()));
        MemberAccess caller = members.get(user.userId());
        if (caller == null) {
            throw refused("You are not a member of this household");
        }
        for (GrantRequest request : requests) {
            MemberAccess recipient = members.get(request.userId());
            if (recipient == null) {
                throw refused("The recipient is not a member of this household");
            }
            if (!recipient.permissions().contains(permissionToHold(request.keyKind()))) {
                throw refused("A %s may not hold the %s key".formatted(recipient.role(), request.keyKind()));
            }
            if (userKeys.findById(request.userId()).isEmpty()) {
                throw refused("The recipient has no public key yet");
            }
            if (grants.existsByHouseholdIdAndUserIdAndKeyKindAndKeyVersion(householdId, request.userId(), request.keyKind(), request.keyVersion())) {
                continue;
            }
            boolean callerHolds = grants.existsByHouseholdIdAndUserIdAndKeyKindAndKeyVersion(householdId, user.userId(), request.keyKind(), request.keyVersion());
            boolean startsHousehold = request.keyVersion() == FIRST_VERSION
                    && caller.permissions().contains(Permission.HOUSEHOLD_DELETE)
                    && !grants.existsByHouseholdIdAndKeyKind(householdId, request.keyKind());
            if (!callerHolds && !startsHousehold) {
                throw refused("Only a member holding this key may grant it");
            }
            grants.saveAndFlush(HouseholdKeyGrant.of(householdId, request.userId(), request.keyKind(), request.keyVersion(),
                    request.wrappedKey(), user.userId(), clock.instant()));
            auditService.record(AuditRecord.of(AuditAction.KEY_GRANTED, AuditEntityType.HOUSEHOLD)
                    .actorId(user.userId()).householdId(householdId).entityId(householdId)
                    .after(Map.of("recipient", request.userId().toString(), "kind", request.keyKind().name(), "version", request.keyVersion()))
                    .build());
        }
    }

    private static Permission permissionToHold(KeyKind kind) {
        return kind == KeyKind.HOUSEHOLD ? Permission.HOUSEHOLD_VIEW : Permission.REPORTS_VIEW;
    }

    private static ApiException refused(String detail) {
        return ApiException.of(ErrorCode.KEY_GRANT_REFUSED, detail);
    }
}
