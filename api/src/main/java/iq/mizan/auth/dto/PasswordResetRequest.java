package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String identifier) {
}
