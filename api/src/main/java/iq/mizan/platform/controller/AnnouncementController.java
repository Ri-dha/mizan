package iq.mizan.platform.controller;

import java.util.List;

import iq.mizan.platform.dto.AnnouncementResponse;
import iq.mizan.platform.service.AnnouncementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/announcements")
@AllArgsConstructor
@Tag(name = "Announcements", description = "Notices from the people who run the service")
public class AnnouncementController {

    private final AnnouncementService announcements;

    @Operation(summary = "Announcements showing now, newest first")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AnnouncementResponse>> live() {
        return ResponseEntity.ok(announcements.live());
    }
}
