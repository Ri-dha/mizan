package iq.mizan.admin.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import iq.mizan.admin.dto.ElevateRequest;
import iq.mizan.admin.dto.GrantOperatorRequest;
import iq.mizan.admin.dto.PollRequest;
import iq.mizan.admin.dto.PriceCorrectionRequest;
import iq.mizan.admin.dto.ReasonRequest;
import iq.mizan.admin.dto.SessionResponse;
import iq.mizan.admin.dto.SettingUpdateRequest;
import iq.mizan.admin.service.AdminOverviewService;
import iq.mizan.admin.service.AdminPriceService;
import iq.mizan.auth.controller.ClientRequest;
import iq.mizan.auth.service.AccountSupportService;
import iq.mizan.auth.service.OperatorService;
import iq.mizan.common.audit.AuditAction;
import iq.mizan.common.audit.AuditSearch;
import iq.mizan.common.exception.ApiException;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.security.CurrentUser;
import iq.mizan.market.service.IqWealthPoller;
import iq.mizan.market.service.MarketService;
import iq.mizan.platform.dto.AnnouncementRequest;
import iq.mizan.platform.dto.AnnouncementResponse;
import iq.mizan.platform.dto.SettingResponse;
import iq.mizan.platform.service.AnnouncementService;
import iq.mizan.platform.service.SettingKey;
import iq.mizan.platform.service.SettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR admin-1: operator endpoints. The role is checked against the database on every call;
 * a change also needs the password again within the elevation window. Nothing here can read a
 * household's records.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("isAuthenticated()")
@AllArgsConstructor
@Tag(name = "Admin", description = "Running the service: settings, feeds, prices, accounts, announcements, audit")
public class AdminController {

    private final OperatorService operators;
    private final AdminOverviewService overview;
    private final SettingsService settings;
    private final AnnouncementService announcements;
    private final AccountSupportService accounts;
    private final AuditSearch auditSearch;
    private final AdminPriceService adminPrices;
    private final MarketService marketService;
    private final IqWealthPoller iqWealthPoller;

    private UUID read(CurrentUser user) {
        operators.requireOperator(user.userId());
        return user.userId();
    }

    private UUID change(CurrentUser user) {
        operators.requireElevated(user.userId());
        return user.userId();
    }

    @Operation(summary = "Whether the caller is an operator and until when changes are allowed without the password")
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(@AuthenticationPrincipal CurrentUser user) {
        boolean operator = operators.isOperator(user.userId());
        return ResponseEntity.ok(new SessionResponse(operator, operator ? operators.elevatedUntil(user.userId()) : null));
    }

    @Operation(summary = "Re-enter the password (as the device-derived auth key) to allow changes for a few minutes")
    @PostMapping("/session")
    public ResponseEntity<SessionResponse> elevate(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ElevateRequest request,
                                                   HttpServletRequest http) {
        Instant until = operators.elevate(user.userId(), request.authKey(), ClientRequest.ip(http));
        return ResponseEntity.ok(new SessionResponse(true, until));
    }

    @Operation(summary = "Counts, sizes and feed health across the service")
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewService.Overview> overview(@AuthenticationPrincipal CurrentUser user) {
        read(user);
        return ResponseEntity.ok(overview.overview());
    }

    @Operation(summary = "Settings operators may change, with their defaults; secrets are masked")
    @GetMapping("/settings")
    public ResponseEntity<List<SettingResponse>> settings(@AuthenticationPrincipal CurrentUser user) {
        read(user);
        return ResponseEntity.ok(settings.list());
    }

    @PutMapping("/settings/{key}")
    public ResponseEntity<List<SettingResponse>> putSetting(@AuthenticationPrincipal CurrentUser user, @PathVariable SettingKey key,
                                                            @Valid @RequestBody SettingUpdateRequest request) {
        settings.put(key, request.value(), change(user));
        return ResponseEntity.ok(settings.list());
    }

    @Operation(summary = "Drop the operator's value so the environment default applies again")
    @DeleteMapping("/settings/{key}")
    public ResponseEntity<List<SettingResponse>> resetSetting(@AuthenticationPrincipal CurrentUser user, @PathVariable SettingKey key) {
        settings.reset(key, change(user));
        return ResponseEntity.ok(settings.list());
    }

    @Operation(summary = "Fetch prices now: DOLLAR spends one IQWealth request; ALL refreshes the other feeds")
    @PostMapping("/feeds/poll")
    public ResponseEntity<AdminOverviewService.Overview> poll(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PollRequest request) {
        change(user);
        if (request.target() == PollRequest.Target.DOLLAR) {
            iqWealthPoller.pollAndStore();
        } else {
            marketService.refresh();
        }
        return ResponseEntity.ok(overview.overview());
    }

    @Operation(summary = "Record a corrected price; it becomes the current quote and a new change in the history")
    @PostMapping("/prices/corrections")
    public ResponseEntity<Void> correct(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PriceCorrectionRequest request) {
        adminPrices.correct(request.instrument(), request.priceMicros(), request.reason(), change(user));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Remove a wrong price change from the history")
    @DeleteMapping("/prices/changes/{id}")
    public ResponseEntity<Void> deleteChange(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @Valid @RequestBody ReasonRequest request) {
        adminPrices.delete(id, request.reason(), change(user));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Find accounts by the start of an email, or part of a name")
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountSupportService.AccountSummary>> accounts(@AuthenticationPrincipal CurrentUser user, @RequestParam String query) {
        read(user);
        return ResponseEntity.ok(accounts.search(query));
    }

    @Operation(summary = "Suspend an account: its sessions end and it cannot sign in until restored")
    @PostMapping("/accounts/{userId}/suspend")
    public ResponseEntity<AccountSupportService.AccountSummary> suspend(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId,
                                                                        @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(accounts.suspend(userId, change(user), request.reason()));
    }

    @PostMapping("/accounts/{userId}/restore")
    public ResponseEntity<AccountSupportService.AccountSummary> restore(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        return ResponseEntity.ok(accounts.restore(userId, change(user)));
    }

    @Operation(summary = "Sign the account out everywhere")
    @PostMapping("/accounts/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        accounts.revokeSessions(userId, change(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{userId}/cancel-deletion")
    public ResponseEntity<AccountSupportService.AccountSummary> cancelDeletion(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId,
                                                                               HttpServletRequest http) {
        return ResponseEntity.ok(accounts.cancelDeletion(userId, change(user), ClientRequest.ip(http)));
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<AnnouncementResponse>> announcements(@AuthenticationPrincipal CurrentUser user) {
        read(user);
        return ResponseEntity.ok(announcements.recent());
    }

    @Operation(summary = "Show a notice to everyone, in English and Arabic")
    @PostMapping("/announcements")
    public ResponseEntity<AnnouncementResponse> publish(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcements.publish(request, change(user)));
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Void> retract(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        announcements.retract(id, change(user));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search the audit log, newest first; page with before")
    @GetMapping("/audit")
    public ResponseEntity<List<AuditSearch.Entry>> audit(@AuthenticationPrincipal CurrentUser user,
                                                         @RequestParam(required = false) String action,
                                                         @RequestParam(required = false) UUID actorId,
                                                         @RequestParam(required = false) UUID entityId,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
                                                         @RequestParam(defaultValue = "50") int limit) {
        read(user);
        if (action != null && !action.isBlank() && java.util.Arrays.stream(AuditAction.values()).noneMatch(a -> a.name().equals(action))) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Unknown audit action");
        }
        return ResponseEntity.ok(auditSearch.search(action, actorId, entityId, before, limit));
    }

    @GetMapping("/operators")
    public ResponseEntity<List<OperatorService.OperatorSummary>> operatorList(@AuthenticationPrincipal CurrentUser user) {
        read(user);
        return ResponseEntity.ok(operators.operators());
    }

    @PostMapping("/operators")
    public ResponseEntity<List<OperatorService.OperatorSummary>> grant(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GrantOperatorRequest request) {
        operators.grant(request.identifier(), change(user));
        return ResponseEntity.ok(operators.operators());
    }

    @DeleteMapping("/operators/{userId}")
    public ResponseEntity<List<OperatorService.OperatorSummary>> revoke(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID userId) {
        operators.revoke(userId, change(user));
        return ResponseEntity.ok(operators.operators());
    }
}
