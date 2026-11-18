package iq.mizan.sync.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record ConflictResponse(
        UUID id,
        String table,
        UUID rowId,
        String field,
        @Nullable Object clientValue,
        @Nullable Object serverValue,
        String clientClock,
        String serverClock,
        Instant detectedAt) {
}
