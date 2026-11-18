package iq.mizan.notification.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record SubscriptionResponse(UUID id, String endpoint, @Nullable String userAgent, Instant createdAt) {
}
