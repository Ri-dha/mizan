package iq.mizan.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The new key set, made on the device; {@code confirm} must be the word RESET. */
public record ResetKeysRequest(@NotBlank @Size(max = 4096) String wrappedByPassword, @NotBlank @Size(max = 4096) String wrappedByRecovery,
                               @NotBlank @Size(max = 4096) String publicKey, @NotBlank @Size(max = 8192) String encryptedPrivateKey,
                               @NotBlank String confirm) {
}
