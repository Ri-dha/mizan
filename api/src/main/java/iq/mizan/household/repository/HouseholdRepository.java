package iq.mizan.household.repository;

import java.util.UUID;

import iq.mizan.household.entity.Household;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {
}
