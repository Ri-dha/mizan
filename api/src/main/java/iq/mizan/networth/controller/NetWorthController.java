package iq.mizan.networth.controller;

import java.util.List;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.networth.dto.MonthCloseResponse;
import iq.mizan.networth.dto.NetWorthResponse;
import iq.mizan.networth.service.MonthCloseService;
import iq.mizan.networth.service.NetWorthCalculator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@AllArgsConstructor
@Validated
@Tag(name = "Net worth", description = "The household's net worth and its month-end snapshots")
public class NetWorthController {

    private static final String MONTH_KEY = "^\\d{4}-\\d{2}$";

    private final NetWorthCalculator calculator;
    private final MonthCloseService monthCloseService;

    @Operation(summary = "Net worth right now, computed on the server from the synced ledgers")
    @GetMapping("/networth/current")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<NetWorthResponse> current(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(calculator.compute(user.householdId()));
    }

    @Operation(summary = "Which months are closed")
    @GetMapping("/months")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<MonthCloseResponse>> months(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(monthCloseService.list(user.householdId()));
    }

    @Operation(summary = "Close a month: takes an immutable net worth snapshot (BR-14)")
    @PostMapping("/months/{monthKey}/close")
    @PreAuthorize("hasAuthority('PLAN_EDIT')")
    public ResponseEntity<MonthCloseResponse> close(
            @AuthenticationPrincipal CurrentUser user, @PathVariable @Pattern(regexp = MONTH_KEY) String monthKey) {
        return ResponseEntity.ok(monthCloseService.close(user.householdId(), user.userId(), monthKey));
    }

    @Operation(summary = "Reopen a closed month; recorded in the audit trail")
    @PostMapping("/months/{monthKey}/reopen")
    @PreAuthorize("hasAuthority('PLAN_EDIT')")
    public ResponseEntity<MonthCloseResponse> reopen(
            @AuthenticationPrincipal CurrentUser user, @PathVariable @Pattern(regexp = MONTH_KEY) String monthKey) {
        return ResponseEntity.ok(monthCloseService.reopen(user.householdId(), user.userId(), monthKey));
    }
}
