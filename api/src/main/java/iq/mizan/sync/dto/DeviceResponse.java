package iq.mizan.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String name, long lastPullSeq, Instant lastSeenAt) {
}
