package iq.mizan.report.controller;

import iq.mizan.common.security.CurrentUser;
import iq.mizan.report.dto.AnnualReportResponse;
import iq.mizan.report.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@AllArgsConstructor
@Tag(name = "Reports", description = "Aggregates over shared records; the advisor's view")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Income, spending, saving rate and closing net worth per month of a year (FR-RPT-03)")
    @GetMapping("/annual")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<AnnualReportResponse> annual(@AuthenticationPrincipal CurrentUser user, @RequestParam int year) {
        return ResponseEntity.ok(reportService.annual(user.householdId(), year));
    }
}
