package iq.mizan.vault.dto;

/** {@code legacyRows} counts plain rows the household still has; zero means everything is encrypted. */
public record VaultStatusResponse(boolean migrated, long legacyRows) {
}
