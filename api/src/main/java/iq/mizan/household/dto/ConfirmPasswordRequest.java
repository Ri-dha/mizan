package iq.mizan.household.dto;

import jakarta.validation.constraints.NotBlank;

/** Re-authentication by the auth key the device derives from the password (ADR e2e-1). */
public record ConfirmPasswordRequest(@NotBlank String authKey) {
}
