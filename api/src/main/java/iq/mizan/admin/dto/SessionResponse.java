package iq.mizan.admin.dto;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** {@code elevatedUntil} is null when changes need the password again. */
public record SessionResponse(boolean operator, @Nullable Instant elevatedUntil) {
}
