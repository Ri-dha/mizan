package iq.mizan.vault.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** {@code account} is null until the device has created the user's keys (accounts from before ADR e2e-1). */
public record MyKeysResponse(@Nullable AccountKeysResponse account, List<GrantResponse> grants) {
}
