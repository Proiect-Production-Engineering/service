package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;

public record CreateCountryRequest(
    @NotBlank(message = "Country name is required")
    String name,

    @NotBlank(message = "Country code is required")
    String code,

    @NotBlank(message = "IBAN pattern is required")
    @Size(min = 11, max = 30, message = "IBAN pattern must be between 11 and 30 characters")
    String ibanPattern
) {}
