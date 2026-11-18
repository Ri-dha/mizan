package iq.mizan.auth.dto;

import java.util.UUID;

import iq.mizan.household.dto.HouseholdResponse;

import org.jspecify.annotations.Nullable;

public record CurrentUserResponse(
        UUID id,
        @Nullable String email,
        @Nullable String phone,
        String displayName,
        String locale,
        boolean verified,
        java.time.@Nullable Instant deletionRequestedAt,
        /** Runs the service (ADR admin-1): the app shows the operator dashboard. */
        boolean operator,
        HouseholdResponse household) {
}
