package iq.mizan.household.dto;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

public record MemberResponse(UUID userId, String displayName, String contact, HouseholdRole role, Instant joinedAt, boolean you) {
}
