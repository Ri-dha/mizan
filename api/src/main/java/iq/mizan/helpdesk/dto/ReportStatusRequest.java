package iq.mizan.helpdesk.dto;

import jakarta.validation.constraints.NotNull;

public record ReportStatusRequest(@NotNull ReportStatus status) {
}
