package iq.mizan.household.dto;

import java.util.UUID;

import iq.mizan.household.entity.HouseholdRole;

public record MembershipResponse(UUID householdId, String householdName, HouseholdRole role, boolean current) {
}
