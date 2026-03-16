package ro.unibuc.prodeng.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;
import ro.unibuc.prodeng.service.CurrencyExchangeRateService;

@RestController
@RequestMapping("/api/exchange-rates")
@Tag(name = "Exchange Rates", description = "Currency exchange rate management endpoints")
@SecurityRequirement(name = "Authentication")
public class CurrencyExchangeRateController {

    @Autowired
    private CurrencyExchangeRateService exchangeRateService;

    @Operation(summary = "Get all exchange rates", description = "Returns the full exchange rate matrix as a map of currency pair keys to rates")
    @ApiResponse(responseCode = "200", description = "Exchange rates retrieved successfully")
    @GetMapping
    public ResponseEntity<Map<String, Double>> getAllExchangeRates() {
        Map<String, Double> exchangeMatrix = exchangeRateService.getAllExchangeRates();
        return ResponseEntity.ok(exchangeMatrix);
    }

    @Operation(summary = "Get exchange rate", description = "Retrieves the exchange rate between two specific currencies")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Exchange rate found"),
        @ApiResponse(responseCode = "400", description = "Invalid or unsupported currency codes")
    })
    @GetMapping("/rate")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @Parameter(description = "Source currency code (e.g. EUR)") @RequestParam String source,
            @Parameter(description = "Target currency code (e.g. RON)") @RequestParam String target) {
        ExchangeRateResponse rate = exchangeRateService.getExchangeRate(source, target);
        return ResponseEntity.ok(rate);
    }

    @Operation(summary = "Set exchange rate (admin only)", description = "Creates a new exchange rate between two currencies. The inverse rate is maintained automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Exchange rate created"),
        @ApiResponse(responseCode = "400", description = "Invalid request or unsupported currencies")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExchangeRateResponse> setExchangeRate(
            @Valid @RequestBody SetExchangeRateRequest request) {
        ExchangeRateResponse rate = exchangeRateService.setExchangeRate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rate);
    }

    @Operation(summary = "Update exchange rate (admin only)", description = "Updates an existing exchange rate. The inverse rate is maintained automatically.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Exchange rate updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request or unsupported currencies")
    })
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExchangeRateResponse> updateExchangeRate(
            @Valid @RequestBody SetExchangeRateRequest request) {
        ExchangeRateResponse rate = exchangeRateService.setExchangeRate(request);
        return ResponseEntity.ok(rate);
    }
}
