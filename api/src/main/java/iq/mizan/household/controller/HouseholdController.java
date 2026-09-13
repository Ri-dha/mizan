package iq.mizan.household.controller;

import java.util.List;
import java.util.UUID;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.household.dto.ChangeRoleRequest;
import iq.mizan.household.dto.ConfirmPasswordRequest;
import iq.mizan.household.dto.CreateInvitationRequest;
import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.dto.InvitationResponse;
import iq.mizan.household.dto.MemberResponse;
import iq.mizan.household.dto.MembershipResponse;
import iq.mizan.household.dto.TransferOwnershipRequest;
import iq.mizan.household.dto.UpdateHouseholdRequest;
import iq.mizan.household.service.HouseholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households")
@AllArgsConstructor
@Tag(name = "Household", description = "The caller's household, its members and invitations")
public class HouseholdController {

    private final HouseholdService householdService;

    @Operation(summary = "The caller's current household")
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<HouseholdResponse> current(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.current(user.userId()));
    }

    @Operation(summary = "Rename the household or change its month start day (FR-SET-05)")
    @PatchMapping("/current")
    @PreAuthorize("hasAuthority('PLAN_EDIT')")
    public ResponseEntity<HouseholdResponse> update(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateHouseholdRequest request) {
        return ResponseEntity.ok(householdService.updateSettings(user.userId(), request));
    }

    @Operation(summary = "Every household the caller belongs to")
    @GetMapping
    public ResponseEntity<List<MembershipResponse>> memberships(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.memberships(user.userId()));
    }

    @Operation(summary = "Make another of the caller's households current; refresh the session afterwards")
    @PostMapping("/{householdId}/switch")
    public ResponseEntity<HouseholdResponse> switchTo(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID householdId) {
        return ResponseEntity.ok(householdService.switchTo(user.userId(), householdId));
    }

    @Operation(summary = "Active members of the current household")
    @GetMapping("/current/members")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<MemberResponse>> members(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.members(user.userId(), user.householdId()));
    }

    @Operation(summary = "Change a member's role (FR-ACC-06)")
    @PatchMapping("/current/members/{userId}")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<MemberResponse> changeRole(
            @AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(householdService.changeRole(user.userId(), user.householdId(), userId, request.role()));
    }

    @Operation(summary = "Remove a member (FR-ACC-06)")
    @DeleteMapping("/current/members/{userId}")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        householdService.removeMember(user.userId(), user.householdId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Leave the current household; the caller's own household becomes current again")
    @PostMapping("/current/leave")
    public ResponseEntity<HouseholdResponse> leave(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.leave(user.userId(), user.householdId()));
    }

    @Operation(summary = "Hand ownership to another member; the caller becomes a Member")
    @PostMapping("/current/transfer-ownership")
    @PreAuthorize("hasAuthority('HOUSEHOLD_DELETE')")
    public ResponseEntity<Void> transferOwnership(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TransferOwnershipRequest request) {
        householdService.transferOwnership(user.userId(), user.householdId(), request.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Invite someone by link; the token is returned once so the owner can share it (FR-ACC-05)")
    @PostMapping("/current/invitations")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<InvitationResponse> invite(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(householdService.invite(user.userId(), user.householdId(), request.role(), request.contact(), request.accessDays()));
    }

    @Operation(summary = "Invitations not yet accepted, revoked or expired")
    @GetMapping("/current/invitations")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<List<InvitationResponse>> invitations(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.openInvitations(user.householdId()));
    }

    @Operation(summary = "Revoke an invitation")
    @DeleteMapping("/current/invitations/{invitationId}")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID invitationId) {
        householdService.revokeInvitation(user.userId(), user.householdId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ask for the household to be deleted after the grace period; needs the owner's password (FR-ACC-07)")
    @PostMapping("/current/deletion-request")
    @PreAuthorize("hasAuthority('HOUSEHOLD_DELETE')")
    public ResponseEntity<HouseholdResponse> requestDeletion(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ConfirmPasswordRequest request) {
        return ResponseEntity.ok(householdService.requestDeletion(user.userId(), user.householdId(), request.password()));
    }

    @Operation(summary = "Withdraw a pending household deletion")
    @DeleteMapping("/current/deletion-request")
    @PreAuthorize("hasAuthority('HOUSEHOLD_DELETE')")
    public ResponseEntity<HouseholdResponse> cancelDeletion(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.cancelDeletion(user.userId(), user.householdId()));
    }
}
