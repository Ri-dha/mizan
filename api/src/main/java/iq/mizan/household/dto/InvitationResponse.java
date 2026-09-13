package iq.mizan.household.dto;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

/** The token is returned only when the invitation is created, so the owner can share the link. */
public record InvitationResponse(UUID id, HouseholdRole role, String contact, Instant expiresAt, String token) {
}
