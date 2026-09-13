package iq.mizan.market.controller;

import java.time.LocalDate;
import java.util.List;

import iq.mizan.market.dto.HistoryPointResponse;
import iq.mizan.market.dto.QuoteResponse;
import iq.mizan.market.entity.Instrument;
import iq.mizan.market.service.MarketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/market")
@AllArgsConstructor
@Tag(name = "Market", description = "Cached spot prices and dollar rates, with their source and age")
public class MarketController {

    private final MarketService marketService;

    @Operation(summary = "Latest quote per instrument; stale after 24 hours without a refresh")
    @GetMapping("/quotes")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<QuoteResponse>> quotes() {
        return ResponseEntity.ok(marketService.quotes());
    }

    @Operation(summary = "Daily price history for charts and snapshots")
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('HOUSEHOLD_VIEW')")
    public ResponseEntity<List<HistoryPointResponse>> history(
            @RequestParam Instrument instrument,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(marketService.history(instrument, from, to));
    }
}
