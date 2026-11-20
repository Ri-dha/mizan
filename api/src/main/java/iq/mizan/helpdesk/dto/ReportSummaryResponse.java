package iq.mizan.helpdesk.dto;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** The reporter fields are filled for operators only. */
public record ReportSummaryResponse(UUID id, ReportCategory category, String subject, ReportStatus status, Instant createdAt,
                                    Instant lastMessageAt, boolean unread, @Nullable ReportReporter reporter) {

    public record ReportReporter(UUID userId, String displayName, @Nullable String email) {
    }
}
