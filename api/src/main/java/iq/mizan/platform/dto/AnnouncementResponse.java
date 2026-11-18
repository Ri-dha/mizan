package iq.mizan.platform.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record AnnouncementResponse(UUID id, String messageEn, String messageAr, String level, Instant startsAt, @Nullable Instant endsAt,
                                   Instant createdAt, @Nullable Instant retractedAt, boolean live) {
}
