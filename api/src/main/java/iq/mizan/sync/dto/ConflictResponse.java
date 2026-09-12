package iq.mizan.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record ConflictResponse(
        UUID id,
        String table,
        UUID rowId,
        String field,
        Object clientValue,
        Object serverValue,
        String clientClock,
        String serverClock,
        Instant detectedAt) {
}
