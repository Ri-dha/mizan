package iq.mizan.vault.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A household or report key wrapped to one member's public key; only that member can open it. */
@Entity
@Table(name = "household_key_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdKeyGrant {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    /** Null once the account is deleted: the grant stays as evidence for rotation (ADR e2e-4). */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_kind", nullable = false)
    private KeyKind keyKind;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "wrapped_key", nullable = false)
    private String wrappedKey;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static HouseholdKeyGrant of(UUID householdId, UUID userId, KeyKind kind, int version, String wrappedKey,
                                       UUID grantedBy, Instant now) {
        HouseholdKeyGrant grant = new HouseholdKeyGrant();
        grant.id = UUID.randomUUID();
        grant.householdId = householdId;
        grant.userId = userId;
        grant.keyKind = kind;
        grant.keyVersion = version;
        grant.wrappedKey = wrappedKey;
        grant.grantedBy = grantedBy;
        grant.createdAt = now;
        return grant;
    }
}
