package iq.mizan.vault.repository;

import java.util.List;
import java.util.UUID;

import iq.mizan.vault.entity.HouseholdKeyGrant;
import iq.mizan.vault.entity.KeyKind;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdKeyGrantRepository extends JpaRepository<HouseholdKeyGrant, UUID> {

    List<HouseholdKeyGrant> findByHouseholdIdAndUserIdOrderByKeyKindAscKeyVersionAsc(UUID householdId, UUID userId);

    boolean existsByHouseholdIdAndUserIdAndKeyKindAndKeyVersion(UUID householdId, UUID userId, KeyKind kind, int version);

    boolean existsByHouseholdIdAndKeyKind(UUID householdId, KeyKind kind);

    List<HouseholdKeyGrant> findByHouseholdIdAndKeyKindAndKeyVersion(UUID householdId, KeyKind kind, int version);

    List<HouseholdKeyGrant> findByHouseholdIdAndKeyKind(UUID householdId, KeyKind kind);
}
