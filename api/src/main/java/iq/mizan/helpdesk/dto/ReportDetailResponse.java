package iq.mizan.helpdesk.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record ReportDetailResponse(UUID id, ReportCategory category, String subject, ReportStatus status, @Nullable String appVersion,
                                   @Nullable String platform, Instant createdAt, List<ReportMessage> messages,
                                   ReportSummaryResponse.@Nullable ReportReporter reporter) {

    public record ReportMessage(UUID id, boolean fromOperator, @Nullable String authorName, String body, Instant createdAt,
                                List<ReportAttachment> attachments) {
    }

    /** {@code removedAt}: the photo was deleted after the report had been resolved for a while. */
    public record ReportAttachment(UUID id, String contentType, int sizeBytes, @Nullable Instant removedAt) {
    }
}
