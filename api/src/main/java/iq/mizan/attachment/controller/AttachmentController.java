package iq.mizan.attachment.controller;

import java.util.UUID;

import iq.mizan.attachment.dto.PresignedUrlResponse;
import iq.mizan.attachment.service.AttachmentService;
import iq.mizan.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attachments")
@AllArgsConstructor
@Tag(name = "Attachments", description = "Short-lived object storage URLs for synced attachment rows")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(summary = "A URL to PUT the encrypted bytes of a synced attachment")
    @PostMapping("/{id}/upload-url")
    @PreAuthorize("hasAuthority('RECORD_WRITE')")
    public ResponseEntity<PresignedUrlResponse> uploadUrl(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(attachmentService.uploadUrl(user, id));
    }

    @Operation(summary = "A URL to GET the encrypted bytes of a synced attachment")
    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<PresignedUrlResponse> downloadUrl(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(attachmentService.downloadUrl(user, id));
    }
}
