package iq.mizan.household.dto;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

public record HouseholdResponse(
        UUID id,
        String name,
        String baseCurrency,
        int monthStartDay,
        HouseholdRole role,
        Instant deletionRequestedAt) {
}
