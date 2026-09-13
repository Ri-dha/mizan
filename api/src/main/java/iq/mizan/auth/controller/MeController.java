package iq.mizan.auth.controller;

import iq.mizan.auth.dto.CurrentUserResponse;
import iq.mizan.auth.service.AccountDeletionService;
import iq.mizan.auth.service.CurrentUserService;
import iq.mizan.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@AllArgsConstructor
@Tag(name = "Account", description = "The signed-in account")
public class MeController {

    private final CurrentUserService currentUserService;
    private final AccountDeletionService accountDeletionService;

    @Operation(summary = "The signed-in account and its household")
    @GetMapping
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(currentUserService.describe(user.userId()));
    }

    @Operation(summary = "Ask for the account and its household to be deleted after the grace period (FR-ACC-08)")
    @PostMapping("/deletion-request")
    public ResponseEntity<CurrentUserResponse> requestDeletion(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        accountDeletionService.request(user.userId(), ClientRequest.ip(request));
        return ResponseEntity.ok(currentUserService.describe(user.userId()));
    }

    @Operation(summary = "Withdraw a pending deletion request")
    @DeleteMapping("/deletion-request")
    public ResponseEntity<CurrentUserResponse> cancelDeletion(@AuthenticationPrincipal CurrentUser user, HttpServletRequest request) {
        accountDeletionService.cancel(user.userId(), ClientRequest.ip(request));
        return ResponseEntity.ok(currentUserService.describe(user.userId()));
    }
}
