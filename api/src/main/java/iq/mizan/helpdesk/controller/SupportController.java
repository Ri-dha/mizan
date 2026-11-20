package iq.mizan.helpdesk.controller;

import java.util.List;
import java.util.UUID;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.helpdesk.dto.NewReportForm;
import iq.mizan.helpdesk.dto.ReplyForm;
import iq.mizan.helpdesk.dto.ReportDetailResponse;
import iq.mizan.helpdesk.dto.ReportSummaryResponse;
import iq.mizan.helpdesk.service.SupportReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support/reports")
@PreAuthorize("isAuthenticated()")
@AllArgsConstructor
@Tag(name = "Support", description = "Problem and feedback reports to the people who run the service; readable by them, not end-to-end encrypted")
public class SupportController {

    private final SupportReportService reports;

    @Operation(summary = "Send a report with up to four JPEG or PNG images; location and camera data are removed from them")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportDetailResponse> create(@AuthenticationPrincipal CurrentUser user, @Valid @ModelAttribute NewReportForm form) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reports.create(user.userId(), form.category(), form.subject(), form.body(), form.appVersion(), form.platform(), form.images()));
    }

    @Operation(summary = "The caller's reports, latest activity first")
    @GetMapping
    public ResponseEntity<List<ReportSummaryResponse>> mine(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(reports.mine(user.userId()));
    }

    @Operation(summary = "One of the caller's reports with its conversation; marks the replies read")
    @GetMapping("/{id}")
    public ResponseEntity<ReportDetailResponse> open(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(reports.openForUser(user.userId(), id));
    }

    @Operation(summary = "Answer in the conversation; a resolved report opens again")
    @PostMapping(path = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportDetailResponse> reply(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @ModelAttribute ReplyForm form) {
        return ResponseEntity.ok(reports.replyAsUser(user.userId(), id, form.body(), form.images()));
    }

    @Operation(summary = "An image attached to one of the caller's reports")
    @GetMapping("/{id}/images/{imageId}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID imageId) {
        return SupportImages.serve(reports.imageForUser(user.userId(), id, imageId));
    }
}
