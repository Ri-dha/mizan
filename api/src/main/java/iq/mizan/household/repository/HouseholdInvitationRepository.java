package iq.mizan.household.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdInvitation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {

    Optional<HouseholdInvitation> findByTokenHash(String tokenHash);

    List<HouseholdInvitation> findByHouseholdIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID householdId);
}
