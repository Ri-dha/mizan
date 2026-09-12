package iq.mizan.sync.controller;

import java.util.List;
import java.util.UUID;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.sync.dto.ConflictResponse;
import iq.mizan.sync.dto.DeviceResponse;
import iq.mizan.sync.dto.RegisterDeviceRequest;
import iq.mizan.sync.dto.SyncPullResponse;
import iq.mizan.sync.dto.SyncPushRequest;
import iq.mizan.sync.dto.SyncPushResponse;
import iq.mizan.sync.service.DeviceService;
import iq.mizan.sync.service.SyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
@AllArgsConstructor
@Tag(name = "Sync", description = "Offline-first change feed: push local writes, pull everyone else's")
public class SyncController {

    private final DeviceService deviceService;
    private final SyncService syncService;

    @Operation(summary = "Register the calling device")
    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<DeviceResponse> registerDevice(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RegisterDeviceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.register(user, request.name()));
    }

    @Operation(summary = "Push local changes",
            description = "Per-field last-write-wins. Fields that lost to a newer server value come back as conflicts.")
    @PostMapping("/push")
    @PreAuthorize("hasAuthority('RECORD_WRITE')")
    public ResponseEntity<SyncPushResponse> push(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SyncPushRequest request) {
        return ResponseEntity.ok(syncService.push(user, request));
    }

    @Operation(summary = "Pull changes after a sequence number")
    @GetMapping("/pull")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<SyncPullResponse> pull(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam UUID deviceId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(syncService.pull(user, deviceId, since, limit));
    }

    @Operation(summary = "Conflicts recorded for a device")
    @GetMapping("/conflicts")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<ConflictResponse>> conflicts(
            @AuthenticationPrincipal CurrentUser user, @RequestParam UUID deviceId) {
        return ResponseEntity.ok(syncService.conflicts(user, deviceId));
    }
}
