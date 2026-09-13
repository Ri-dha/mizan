package iq.mizan.household.entity;

import java.time.Duration;
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

/** FR-ACC-05: a link that grants one membership, valid until it expires, is accepted or is revoked. */
@Entity
@Table(name = "household_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdInvitation {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HouseholdRole role;

    private String contact;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static HouseholdInvitation issue(UUID householdId, UUID invitedBy, HouseholdRole role, String contact,
                                            String tokenHash, Duration ttl, Instant now) {
        HouseholdInvitation invitation = new HouseholdInvitation();
        invitation.id = UUID.randomUUID();
        invitation.householdId = householdId;
        invitation.invitedBy = invitedBy;
        invitation.role = role;
        invitation.contact = contact;
        invitation.tokenHash = tokenHash;
        invitation.expiresAt = now.plus(ttl);
        invitation.createdAt = now;
        return invitation;
    }

    public boolean isOpen(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isExpired(Instant now) {
        return acceptedAt == null && revokedAt == null && !expiresAt.isAfter(now);
    }

    public void accept(UUID userId, Instant now) {
        this.acceptedBy = userId;
        this.acceptedAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
