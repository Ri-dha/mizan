package iq.mizan.helpdesk.controller;

import java.util.List;
import java.util.UUID;

import iq.mizan.auth.service.OperatorService;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.helpdesk.dto.ReplyForm;
import iq.mizan.helpdesk.dto.ReportDetailResponse;
import iq.mizan.helpdesk.dto.ReportStatus;
import iq.mizan.helpdesk.dto.ReportStatusRequest;
import iq.mizan.helpdesk.dto.ReportSummaryResponse;
import iq.mizan.helpdesk.service.SupportReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADR admin-1 applies: reading needs the operator role, replying and changing status the password again. */
@RestController
@RequestMapping("/api/v1/admin/support/reports")
@PreAuthorize("isAuthenticated()")
@AllArgsConstructor
@Tag(name = "Admin", description = "Running the service: settings, feeds, prices, accounts, announcements, audit")
public class SupportAdminController {

    private final OperatorService operators;
    private final SupportReportService reports;

    @Operation(summary = "Reports to handle, unread first; filter by status")
    @GetMapping
    public ResponseEntity<List<ReportSummaryResponse>> queue(@AuthenticationPrincipal CurrentUser user,
                                                             @RequestParam(required = false) ReportStatus status) {
        operators.requireOperator(user.userId());
        return ResponseEntity.ok(reports.queue(status));
    }

    @Operation(summary = "A report with its conversation and the reporter; marks it read for operators")
    @GetMapping("/{id}")
    public ResponseEntity<ReportDetailResponse> open(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        operators.requireOperator(user.userId());
        return ResponseEntity.ok(reports.openForOperator(id));
    }

    @Operation(summary = "Reply to the reporter, who gets a push notification")
    @PostMapping(path = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportDetailResponse> reply(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @ModelAttribute ReplyForm form) {
        operators.requireElevated(user.userId());
        return ResponseEntity.ok(reports.replyAsOperator(user.userId(), id, form.body(), form.images()));
    }

    @Operation(summary = "Set a report's status")
    @PutMapping("/{id}/status")
    public ResponseEntity<ReportDetailResponse> status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
                                                       @Valid @RequestBody ReportStatusRequest request) {
        operators.requireElevated(user.userId());
        return ResponseEntity.ok(reports.setStatus(user.userId(), id, request.status()));
    }

    @Operation(summary = "An image attached to a report")
    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID imageId) {
        operators.requireOperator(user.userId());
        return SupportImages.serve(reports.imageForOperator(id, imageId));
    }
}
