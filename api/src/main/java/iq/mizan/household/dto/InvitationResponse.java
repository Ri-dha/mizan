package iq.mizan.household.dto;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

import org.jspecify.annotations.Nullable;

/** The token is returned only when the invitation is created, so the owner can share the link. */
public record InvitationResponse(UUID id, HouseholdRole role, @Nullable String contact, Instant expiresAt, @Nullable String token) {
}
