package iq.mizan.notification.repository;

import java.util.UUID;

public record Recipient(UUID userId, UUID householdId) {
}
