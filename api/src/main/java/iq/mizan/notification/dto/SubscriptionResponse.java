package iq.mizan.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(UUID id, String endpoint, String userAgent, Instant createdAt) {
}
