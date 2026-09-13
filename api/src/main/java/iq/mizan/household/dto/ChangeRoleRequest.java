package iq.mizan.household.dto;

import iq.mizan.household.entity.HouseholdRole;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull HouseholdRole role) {
}
