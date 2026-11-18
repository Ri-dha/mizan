package iq.mizan.sync.service;

import java.util.Map;
import java.util.UUID;

/** One encrypted record: ciphertext per field plus the two plain fields, visibility and deletedAt. */
public record VaultRow(UUID id, UUID ownerId, String tableName, Map<String, Object> fields, Map<String, String> clocks) {
}
