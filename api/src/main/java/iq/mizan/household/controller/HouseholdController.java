package iq.mizan.household.controller;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.household.dto.HouseholdResponse;
import iq.mizan.household.dto.UpdateHouseholdRequest;
import iq.mizan.household.service.HouseholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households/current")
@AllArgsConstructor
@Tag(name = "Household", description = "The caller's household")
public class HouseholdController {

    private final HouseholdService householdService;

    @Operation(summary = "The caller's household")
    @GetMapping
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<HouseholdResponse> current(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(householdService.current(user.userId()));
    }

    @Operation(summary = "Rename the household or change its month start day (FR-SET-05)")
    @PatchMapping
    @PreAuthorize("hasAuthority('PLAN_EDIT')")
    public ResponseEntity<HouseholdResponse> update(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateHouseholdRequest request) {
        return ResponseEntity.ok(householdService.updateSettings(user.userId(), request));
    }
}
