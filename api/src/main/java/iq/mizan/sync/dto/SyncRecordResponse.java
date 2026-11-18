package iq.mizan.sync.dto;

import java.util.Map;
import java.util.UUID;

/**
 * One row from the feed. {@code encrypted} rows carry ciphertext in every field but visibility
 * and deletedAt (ADR e2e-1); the rest are legacy plain rows still waiting for migration.
 */
public record SyncRecordResponse(
        String table,
        UUID rowId,
        UUID ownerId,
        Map<String, Object> fields,
        Map<String, String> clocks,
        long seq,
        boolean encrypted) {
}
