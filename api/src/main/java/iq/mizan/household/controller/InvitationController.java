package iq.mizan.household.controller;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.dto.InvitationPreviewResponse;
import iq.mizan.household.service.HouseholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@AllArgsConstructor
@Tag(name = "Invitations", description = "Joining a household by link")
public class InvitationController {

    private final HouseholdService householdService;

    @Operation(summary = "What an invitation link leads to; public so the join page can show it before sign-in")
    @GetMapping("/{token}")
    public ResponseEntity<InvitationPreviewResponse> preview(@PathVariable String token) {
        return ResponseEntity.ok(householdService.preview(token));
    }

    @Operation(summary = "Join the household behind the link; refresh the session afterwards to act in it")
    @PostMapping("/{token}/accept")
    public ResponseEntity<HouseholdResponse> accept(@AuthenticationPrincipal CurrentUser user, @PathVariable String token) {
        return ResponseEntity.ok(householdService.accept(user.userId(), token));
    }
}
