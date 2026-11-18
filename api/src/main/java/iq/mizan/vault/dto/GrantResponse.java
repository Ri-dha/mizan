package iq.mizan.vault.dto;

import java.util.UUID;

import iq.mizan.vault.entity.KeyKind;

import org.jspecify.annotations.Nullable;

public record GrantResponse(KeyKind keyKind, int keyVersion, String wrappedKey, @Nullable UUID grantedBy) {
}
