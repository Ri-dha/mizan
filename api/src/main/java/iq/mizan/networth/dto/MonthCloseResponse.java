package iq.mizan.networth.dto;

import java.time.Instant;
import java.util.UUID;

public record MonthCloseResponse(String monthKey, UUID snapshotId, Instant closedAt, Instant reopenedAt, boolean closed) {
}
