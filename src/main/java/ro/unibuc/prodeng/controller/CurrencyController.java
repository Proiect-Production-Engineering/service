package ro.unibuc.prodeng.controller;

import java.util.List;

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
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.response.CurrencyResponse;
import ro.unibuc.prodeng.service.CurrencyService;

@RestController
@RequestMapping("/api/currencies")
@Tag(name = "Currencies", description = "Currency management endpoints")
@SecurityRequirement(name = "Authentication")
public class CurrencyController {

    @Autowired
    private CurrencyService currencyService;

    @Operation(summary = "Get all currencies", description = "Retrieves a list of all registered currencies")
    @ApiResponse(responseCode = "200", description = "Currencies retrieved successfully")
    @GetMapping
    public ResponseEntity<List<CurrencyResponse>> getAllCurrencies() {
        List<CurrencyResponse> currencies = currencyService.getAllCurrencies();
        return ResponseEntity.ok(currencies);
    }

    @Operation(summary = "Get currency by ID", description = "Retrieves a currency by its unique identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Currency found"),
        @ApiResponse(responseCode = "404", description = "Currency not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CurrencyResponse> getCurrencyById(
            @Parameter(description = "Currency ID") @PathVariable String id) {
        CurrencyResponse currency = currencyService.getCurrencyById(id);
        return ResponseEntity.ok(currency);
    }

    @Operation(summary = "Get currency by code", description = "Retrieves a currency by its ISO 4217 code (e.g. EUR, RON, GBP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Currency found"),
        @ApiResponse(responseCode = "404", description = "Currency not found")
    })
    @GetMapping("/by-code")
    public ResponseEntity<CurrencyResponse> getCurrencyByCode(
            @Parameter(description = "ISO 4217 currency code") @RequestParam String code) {
        CurrencyResponse currency = currencyService.getCurrencyByCode(code);
        return ResponseEntity.ok(currency);
    }

    @Operation(summary = "Create a new currency (admin only)", description = "Registers a new currency with a name and ISO 4217 code")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Currency created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or currency code already exists")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CurrencyResponse> createCurrency(
            @Valid @RequestBody CreateCurrencyRequest request) {
        CurrencyResponse currency = currencyService.createCurrency(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(currency);
    }

    @Operation(summary = "Delete currency (admin only)", description = "Deletes a currency by its unique identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Currency deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Currency not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCurrency(
            @Parameter(description = "Currency ID") @PathVariable String id) {
        currencyService.deleteCurrency(id);
        return ResponseEntity.noContent().build();
    }
}
