package iq.mizan.household.dto;

import java.time.Instant;

import iq.mizan.household.entity.HouseholdRole;

public record InvitationPreviewResponse(String householdName, String invitedBy, HouseholdRole role, Instant expiresAt) {
}
