package iq.mizan.household.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.household.entity.Membership;
import iq.mizan.household.entity.MembershipStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findFirstByUserIdAndStatusOrderByJoinedAtAsc(UUID userId, MembershipStatus status);

    Optional<Membership> findFirstByUserIdAndStatusAndCurrentTrue(UUID userId, MembershipStatus status);

    Optional<Membership> findByHouseholdIdAndUserId(UUID householdId, UUID userId);

    List<Membership> findByUserIdAndStatusOrderByJoinedAtAsc(UUID userId, MembershipStatus status);

    List<Membership> findByHouseholdIdAndStatusOrderByJoinedAtAsc(UUID householdId, MembershipStatus status);
}
