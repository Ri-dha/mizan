package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code legacyPassword} is sent only by accounts created before end-to-end encryption, once, so
 * the server can verify it and switch the account to the auth key.
 */
public record LoginRequest(
        @NotBlank String identifier,
        @NotBlank @Pattern(regexp = AuthKeyPattern.REGEX) String authKey,
        @Size(max = 128) String legacyPassword) {
}
