package iq.mizan.household.dto;

import iq.mizan.household.entity.HouseholdRole;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code accessDays} limits how long the membership lasts; advisors default to the configured window. */
public record CreateInvitationRequest(@NotNull HouseholdRole role, @Size(max = 160) String contact, @Min(1) @Max(365) Integer accessDays) {
}
