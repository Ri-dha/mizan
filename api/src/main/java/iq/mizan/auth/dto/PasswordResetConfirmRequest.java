package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A reset restores sign-in only; the device needs the recovery key to unlock the data again. */
public record PasswordResetConfirmRequest(
        @NotBlank String identifier,
        @NotBlank @Size(max = 12) String code,
        @NotBlank @Pattern(regexp = AuthKeyPattern.REGEX) String newAuthKey) {
}
