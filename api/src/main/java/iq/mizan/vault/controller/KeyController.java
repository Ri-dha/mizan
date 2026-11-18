package iq.mizan.vault.controller;

import java.util.List;

import iq.mizan.auth.dto.AccountKeysRequest;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.vault.dto.GrantRequest;
import iq.mizan.vault.dto.KeyMemberResponse;
import iq.mizan.vault.dto.MyKeysResponse;
import iq.mizan.vault.dto.ResetKeysRequest;
import iq.mizan.vault.dto.ResetPreviewResponse;
import iq.mizan.vault.dto.RewrapRequest;
import iq.mizan.vault.service.KeyResetService;
import iq.mizan.vault.service.KeyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
@AllArgsConstructor
@Validated
@Tag(name = "Keys", description = "Wrapped encryption keys; the server stores them but cannot open them (ADR e2e-1)")
public class KeyController {

    private static final int MAX_GRANTS = 50;

    private final KeyService keyService;
    private final KeyResetService keyReset;

    @Operation(summary = "The caller's wrapped user key and the household keys granted to them")
    @GetMapping("/me")
    public ResponseEntity<MyKeysResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(keyService.mine(user));
    }

    @Operation(summary = "Store the caller's keys, once, for an account created before end-to-end encryption")
    @PutMapping("/me")
    public ResponseEntity<MyKeysResponse> store(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AccountKeysRequest request) {
        keyService.storeAccountKeys(user.userId(), request.wrappedByPassword(), request.wrappedByRecovery(), request.publicKey(), request.encryptedPrivateKey());
        return ResponseEntity.ok(keyService.mine(user));
    }

    @Operation(summary = "Replace the password or recovery wrap of the same user key")
    @PatchMapping("/me")
    public ResponseEntity<MyKeysResponse> rewrap(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RewrapRequest request) {
        keyService.rewrap(user, request);
        return ResponseEntity.ok(keyService.mine(user));
    }

    @Operation(summary = "Members with their public keys and the keys they already hold, for granting")
    @GetMapping("/household/members")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<KeyMemberResponse>> members(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(keyService.members(user));
    }

    @Operation(summary = "Hand household or report keys, wrapped to each recipient's public key, to members")
    @PostMapping("/household/grants")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<KeyMemberResponse>> grant(@AuthenticationPrincipal CurrentUser user,
                                                         @Valid @RequestBody @Size(min = 1, max = MAX_GRANTS) List<@Valid GrantRequest> requests) {
        keyService.grant(user, requests);
        return ResponseEntity.ok(keyService.members(user));
    }

    @Operation(summary = "What starting over without a recovery code would remove (ADR e2e-3)")
    @GetMapping("/me/reset-preview")
    public ResponseEntity<ResetPreviewResponse> resetPreview(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(keyReset.preview(user.userId()));
    }

    @Operation(summary = "Replace the account's keys; records sealed under the old ones are removed (ADR e2e-3)")
    @PostMapping("/me/reset")
    public ResponseEntity<ResetPreviewResponse> reset(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ResetKeysRequest request) {
        return ResponseEntity.ok(keyReset.reset(user.userId(), request));
    }
}
