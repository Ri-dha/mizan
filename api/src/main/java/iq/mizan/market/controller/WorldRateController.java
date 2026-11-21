package iq.mizan.market.controller;

import java.time.LocalDate;
import java.util.List;

import iq.mizan.market.dto.CurrencyCatalogueResponse;
import iq.mizan.market.dto.WorldRatePointResponse;
import iq.mizan.market.dto.WorldRatesResponse;
import iq.mizan.market.service.CurrencyCatalogue;
import iq.mizan.market.service.WorldRateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
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
public class WorldRateController {

    private static final CacheControl REVALIDATE = CacheControl.noCache().cachePrivate();

    private final WorldRateService worldRates;
    private final CurrencyCatalogue catalogue;

    @Operation(operationId = "currencyCatalogue", summary = "Currencies a household may count in and the countries offered; open before sign-in for registration")
    @GetMapping("/currencies")
    public ResponseEntity<CurrencyCatalogueResponse> currencies() {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(catalogue.catalogue());
    }

    @Operation(operationId = "worldRates", summary = "The latest rate per US dollar of every currency but the dinar, updated daily")
    @GetMapping("/world-rates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorldRatesResponse> latest() {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(worldRates.latest());
    }

    @Operation(operationId = "worldRateHistory", summary = "One currency's daily rate per US dollar; the last year unless a range is given")
    @GetMapping("/world-rates/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorldRatePointResponse>> history(
            @RequestParam String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().cacheControl(REVALIDATE).body(worldRates.history(currency, from, to));
    }
}
