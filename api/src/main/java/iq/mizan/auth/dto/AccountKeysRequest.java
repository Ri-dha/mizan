package iq.mizan.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The user's key material, every secret already wrapped or encrypted on the device (ADR e2e-1). */
public record AccountKeysRequest(
        @NotBlank @Size(max = 4096) String wrappedByPassword,
        @NotBlank @Size(max = 4096) String wrappedByRecovery,
        @NotBlank @Size(max = 4096) String publicKey,
        @NotBlank @Size(max = 8192) String encryptedPrivateKey) {
}
