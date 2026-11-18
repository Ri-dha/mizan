package iq.mizan.auth.dto;

import java.util.UUID;

import iq.mizan.household.dto.HouseholdResponse;

public record CurrentUserResponse(
        UUID id,
        String email,
        String phone,
        String displayName,
        String locale,
        boolean verified,
        java.time.Instant deletionRequestedAt,
        /** Runs the service (ADR admin-1): the app shows the operator dashboard. */
        boolean operator,
        HouseholdResponse household) {
}
