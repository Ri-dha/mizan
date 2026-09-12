package iq.mizan.household.repository;

import java.util.Optional;
import java.util.UUID;

import iq.mizan.household.entity.Membership;
import iq.mizan.household.entity.MembershipStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findFirstByUserIdAndStatusOrderByJoinedAtAsc(UUID userId, MembershipStatus status);
}
