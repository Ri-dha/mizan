package iq.mizan.vault.dto;

import jakarta.validation.constraints.Size;

/** Either or both: a new password wrap after a reset, a new recovery wrap after regenerating the recovery key. */
public record RewrapRequest(@Size(max = 4096) String wrappedByPassword, @Size(max = 4096) String wrappedByRecovery) {
}
