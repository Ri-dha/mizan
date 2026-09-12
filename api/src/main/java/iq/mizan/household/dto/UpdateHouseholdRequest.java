package iq.mizan.household.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateHouseholdRequest(
        @Size(min = 1, max = 80) String name,
        @Min(1) @Max(28) Integer monthStartDay) {
}
