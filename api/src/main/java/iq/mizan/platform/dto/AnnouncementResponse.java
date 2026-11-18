package iq.mizan.platform.dto;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(UUID id, String messageEn, String messageAr, String level, Instant startsAt, Instant endsAt,
                                   Instant createdAt, Instant retractedAt, boolean live) {
}
