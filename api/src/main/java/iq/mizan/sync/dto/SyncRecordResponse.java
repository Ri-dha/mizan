package iq.mizan.sync.dto;

import java.util.Map;
import java.util.UUID;

public record SyncRecordResponse(
        String table,
        UUID rowId,
        UUID ownerId,
        Map<String, Object> fields,
        Map<String, String> clocks,
        long seq) {
}
