package iq.mizan.household.dto;

import java.time.Instant;
import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

import org.jspecify.annotations.Nullable;

public record MemberResponse(UUID userId, String displayName, String contact, HouseholdRole role, Instant joinedAt, @Nullable Instant expiresAt, boolean you) {
}
