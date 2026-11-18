package iq.mizan.vault.dto;

public record AccountKeysResponse(String wrappedByPassword, String wrappedByRecovery, String publicKey, String encryptedPrivateKey) {
}
