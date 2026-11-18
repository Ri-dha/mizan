package iq.mizan.vault.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
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
import iq.mizan.household.service.HouseholdService;
import iq.mizan.household.service.MemberAccess;
import iq.mizan.sync.service.SharedKeyVersions;
import iq.mizan.vault.dto.KeyStateResponse;
import iq.mizan.vault.dto.RotateKeyRequest;
import iq.mizan.vault.entity.HouseholdKeyGrant;
import iq.mizan.vault.entity.KeyKind;
import iq.mizan.vault.repository.HouseholdKeyGrantRepository;
import iq.mizan.vault.repository.KeyStateRepository;
import iq.mizan.vault.repository.UserKeyRepository;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR e2e-4: a shared key gets a new version once anyone holding the current one has lost access.
 * The server cannot make the key; it decides when one is due, who must receive it, and which
 * device's version wins.
 */
@Service
@AllArgsConstructor
public class KeyRotationService implements SharedKeyVersions {

    private static final Map<Character, KeyKind> SEAL_KINDS = Map.of('h', KeyKind.HOUSEHOLD, 'r', KeyKind.REPORT);

    private final KeyStateRepository states;
    private final HouseholdKeyGrantRepository grants;
    private final UserKeyRepository userKeys;
    private final HouseholdService households;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public OptionalInt current(UUID householdId, char sealKind) {
        KeyKind kind = SEAL_KINDS.get(sealKind);
        if (kind == null) {
            return OptionalInt.empty();
        }
        return states.current(householdId, kind).map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    @Transactional(readOnly = true)
    public List<KeyStateResponse> state(CurrentUser user) {
        Map<UUID, MemberAccess> members = membersOf(user.householdId());
        return states.all(user.householdId()).stream()
                .map(state -> new KeyStateResponse(state.kind(), state.currentVersion(),
                        rotationDue(user.householdId(), state.kind(), state.currentVersion(), members)))
                .toList();
    }

    @Transactional
    public KeyStateResponse rotate(CurrentUser user, RotateKeyRequest request) {
        UUID householdId = user.householdId();
        KeyKind kind = request.keyKind();
        int current = states.lockCurrent(householdId, kind)
                .orElseThrow(() -> refused("This household has no %s key yet".formatted(kind)));
        if (request.keyVersion() != current + 1) {
            throw ApiException.of(ErrorCode.KEY_ROTATION_CONFLICT,
                    "The %s key is at version %d; a rotation must create version %d".formatted(kind, current, current + 1))
                    .property("keyVersion", current);
        }
        Map<UUID, MemberAccess> members = membersOf(householdId);
        if (!KeyHolding.mayHold(members.get(user.userId()), kind)
                || !grants.existsByHouseholdIdAndUserIdAndKeyKindAndKeyVersion(householdId, user.userId(), kind, current)) {
            throw refused("Only a member holding the current %s key may rotate it".formatted(kind));
        }
        if (!rotationDue(householdId, kind, current, members)) {
            throw refused("Everyone holding the %s key still has access; nothing to rotate".formatted(kind));
        }
        requireEveryRecipient(request, members, kind);

        for (RotateKeyRequest.Recipient recipient : request.grants()) {
            grants.save(HouseholdKeyGrant.of(householdId, recipient.userId(), kind, request.keyVersion(),
                    recipient.wrappedKey(), user.userId(), clock.instant()));
        }
        states.advance(householdId, kind, request.keyVersion(), clock.instant());
        List<HouseholdKeyGrant> revoked = grants.findByHouseholdIdAndKeyKind(householdId, kind).stream()
                .filter(grant -> grant.getUserId() == null || !KeyHolding.mayHold(members.get(grant.getUserId()), kind))
                .toList();
        grants.deleteAll(revoked);
        auditService.record(AuditRecord.of(AuditAction.KEY_ROTATED, AuditEntityType.HOUSEHOLD)
                .actorId(user.userId()).householdId(householdId).entityId(householdId)
                .after(Map.of("kind", kind.name(), "version", request.keyVersion(), "recipients", request.grants().size(),
                        "grantsRevoked", revoked.size()))
                .build());
        return new KeyStateResponse(kind, request.keyVersion(), false);
    }

    private boolean rotationDue(UUID householdId, KeyKind kind, int current, Map<UUID, MemberAccess> members) {
        return grants.findByHouseholdIdAndKeyKindAndKeyVersion(householdId, kind, current).stream()
                .anyMatch(grant -> grant.getUserId() == null || !KeyHolding.mayHold(members.get(grant.getUserId()), kind));
    }

    /** Members without a public key yet are left out; they are granted every version once they have one. */
    private void requireEveryRecipient(RotateKeyRequest request, Map<UUID, MemberAccess> members, KeyKind kind) {
        Set<UUID> expected = members.values().stream()
                .filter(member -> KeyHolding.mayHold(member, kind))
                .map(MemberAccess::userId)
                .filter(userKeys::existsById)
                .collect(Collectors.toSet());
        Set<UUID> given = new HashSet<>();
        for (RotateKeyRequest.Recipient recipient : request.grants()) {
            if (!given.add(recipient.userId())) {
                throw refused("Member %s appears twice".formatted(recipient.userId()));
            }
        }
        if (!given.equals(expected)) {
            throw refused("The new %s key must be granted to exactly the members who may hold it".formatted(kind))
                    .property("expected", expected.stream().map(UUID::toString).sorted().toList());
        }
    }

    private Map<UUID, MemberAccess> membersOf(UUID householdId) {
        return households.activeMembers(householdId).stream().collect(Collectors.toMap(MemberAccess::userId, Function.identity()));
    }

    private static ApiException refused(String detail) {
        return ApiException.of(ErrorCode.KEY_ROTATION_REFUSED, detail);
    }
}
