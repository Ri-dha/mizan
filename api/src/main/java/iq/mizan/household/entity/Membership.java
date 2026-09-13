package iq.mizan.household.entity;

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

@Entity
@Table(name = "membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HouseholdRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public static Membership owner(UUID householdId, UUID userId) {
        Membership membership = new Membership();
        membership.id = UUID.randomUUID();
        membership.householdId = householdId;
        membership.userId = userId;
        membership.role = HouseholdRole.OWNER;
        membership.status = MembershipStatus.ACTIVE;
        membership.joinedAt = Instant.now();
        membership.current = true;
        return membership;
    }

    public static Membership join(UUID householdId, UUID userId, HouseholdRole role, Instant expiresAt) {
        Membership membership = new Membership();
        membership.id = UUID.randomUUID();
        membership.householdId = householdId;
        membership.userId = userId;
        membership.role = role;
        membership.status = MembershipStatus.ACTIVE;
        membership.joinedAt = Instant.now();
        membership.expiresAt = expiresAt;
        return membership;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    /** Advisors are let in for a while (§4.2 P4); everyone else until removed. */
    public boolean isActiveAt(Instant now) {
        return isActive() && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void changeRole(HouseholdRole role) {
        this.role = role;
    }

    public void remove() {
        this.status = MembershipStatus.REMOVED;
        this.current = false;
    }

    public void reactivate(HouseholdRole role, Instant expiresAt) {
        this.status = MembershipStatus.ACTIVE;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public void makeCurrent(boolean current) {
        this.current = current;
    }
}
