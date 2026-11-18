package iq.mizan.admin.dto;

import java.time.Instant;

/** {@code elevatedUntil} is null when changes need the password again. */
public record SessionResponse(boolean operator, Instant elevatedUntil) {
}
