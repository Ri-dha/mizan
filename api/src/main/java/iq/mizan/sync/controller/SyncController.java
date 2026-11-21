package iq.mizan.sync.controller;

import iq.mizan.sync.SyncProperties;
import iq.mizan.sync.dto.SyncWaitResponse;
import iq.mizan.sync.service.SyncSignal;
import org.springframework.web.context.request.async.DeferredResult;
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
    private final SyncSignal signal;
    private final SyncProperties properties;

    @Operation(summary = "Register the calling device")
    @PostMapping("/devices")
    @PreAuthorize("hasAnyAuthority('HOUSEHOLD_VIEW', 'RECORD_WRITE', 'REPORTS_VIEW')")
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
    @PreAuthorize("hasAnyAuthority('HOUSEHOLD_VIEW', 'RECORD_WRITE', 'REPORTS_VIEW')")
    public ResponseEntity<SyncPullResponse> pull(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam UUID deviceId,
            @RequestParam(defaultValue = "0") long since,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(syncService.pull(user, deviceId, since, limit));
    }

    @Operation(operationId = "waitForChanges", summary = "Held open until the household's feed moves past `since`, or the wait times out",
            description = "ADR sync-4: answers at once when there is already something to pull. Devices call it again after each answer.")
    @GetMapping("/wait")
    @PreAuthorize("hasAnyAuthority('HOUSEHOLD_VIEW', 'RECORD_WRITE', 'REPORTS_VIEW')")
    public DeferredResult<SyncWaitResponse> waitForChanges(@AuthenticationPrincipal CurrentUser user, @RequestParam long since) {
        // Opened before the check, so a change committed in between still wakes it.
        DeferredResult<SyncWaitResponse> result = signal.open(user.householdId(), user.userId(), properties.waitTimeout().toMillis());
        if (syncService.hasChangesAfter(since)) {
            result.setResult(SyncWaitResponse.CHANGED);
        }
        return result;
    }

    @Operation(summary = "Conflicts recorded for a device")
    @GetMapping("/conflicts")
    @PreAuthorize("hasAnyAuthority('HOUSEHOLD_VIEW', 'RECORD_WRITE', 'REPORTS_VIEW')")
    public ResponseEntity<List<ConflictResponse>> conflicts(
            @AuthenticationPrincipal CurrentUser user, @RequestParam UUID deviceId) {
        return ResponseEntity.ok(syncService.conflicts(user, deviceId));
    }
}
