package iq.mizan.sync.dto;

import java.util.List;
import java.util.UUID;

public record SyncPushResponse(List<UUID> applied, List<ConflictResponse> conflicts) {
}
