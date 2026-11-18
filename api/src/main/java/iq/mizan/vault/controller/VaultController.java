package iq.mizan.vault.controller;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.vault.dto.VaultStatusResponse;
import iq.mizan.vault.service.VaultMigrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vault")
@AllArgsConstructor
@Tag(name = "Vault", description = "Migration of a household's plain records to end-to-end encryption")
public class VaultController {

    private final VaultMigrationService migrationService;

    @Operation(summary = "Whether the household still has plain records on the server")
    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('HOUSEHOLD_VIEW', 'RECORD_WRITE')")
    public ResponseEntity<VaultStatusResponse> status(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(migrationService.status(user));
    }

    @Operation(summary = "Delete the plain copies of records this device has re-uploaded encrypted")
    @PostMapping("/migration/complete")
    @PreAuthorize("hasAuthority('RECORD_WRITE')")
    public ResponseEntity<VaultStatusResponse> complete(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(migrationService.complete(user));
    }
}
