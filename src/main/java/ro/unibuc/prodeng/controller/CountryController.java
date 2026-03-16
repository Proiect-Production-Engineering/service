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
import ro.unibuc.prodeng.request.CreateCountryRequest;
import ro.unibuc.prodeng.response.CountryResponse;
import ro.unibuc.prodeng.service.CountryService;

@RestController
@RequestMapping("/api/countries")
@Tag(name = "Countries", description = "Country management endpoints")
@SecurityRequirement(name = "Authentication")
public class CountryController {

    @Autowired
    private CountryService countryService;

    @Operation(summary = "Get all countries", description = "Retrieves a list of all registered countries")
    @ApiResponse(responseCode = "200", description = "Countries retrieved successfully")
    @GetMapping
    public ResponseEntity<List<CountryResponse>> getAllCountries() {
        List<CountryResponse> countries = countryService.getAllCountries();
        return ResponseEntity.ok(countries);
    }

    @Operation(summary = "Get country by ID", description = "Retrieves a country by its unique identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Country found"),
        @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CountryResponse> getCountryById(
            @Parameter(description = "Country ID") @PathVariable String id) {
        CountryResponse country = countryService.getCountryById(id);
        return ResponseEntity.ok(country);
    }

    @Operation(summary = "Get country by code", description = "Retrieves a country by its ISO 3166-1 alpha-2 code (e.g. RO, FR, GB)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Country found"),
        @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @GetMapping("/by-code")
    public ResponseEntity<CountryResponse> getCountryByCode(
            @Parameter(description = "ISO 3166-1 alpha-2 country code") @RequestParam String code) {
        CountryResponse country = countryService.getCountryByCode(code);
        return ResponseEntity.ok(country);
    }

    @Operation(summary = "Create a new country (admin only)", description = "Registers a new country with a name and ISO code")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Country created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or country code already exists")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CountryResponse> createCountry(
            @Valid @RequestBody CreateCountryRequest request) {
        CountryResponse country = countryService.createCountry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(country);
    }

    @Operation(summary = "Delete country (admin only)", description = "Deletes a country by its unique identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Country deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Country not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCountry(
            @Parameter(description = "Country ID") @PathVariable String id) {
        countryService.deleteCountry(id);
        return ResponseEntity.noContent().build();
    }
}
