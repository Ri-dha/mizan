package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String identifier,
        @NotBlank @Size(max = 12) String code,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
