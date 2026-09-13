package iq.mizan.household.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPasswordRequest(@NotBlank String password) {
}
