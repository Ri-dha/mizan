package iq.mizan.vault.dto;

import java.util.UUID;

import iq.mizan.vault.entity.KeyKind;

public record GrantResponse(KeyKind keyKind, int keyVersion, String wrappedKey, UUID grantedBy) {
}
