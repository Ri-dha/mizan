package iq.mizan.household.dto;

import iq.mizan.household.entity.HouseholdRole;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(@NotNull HouseholdRole role, @Size(max = 160) String contact) {
}
