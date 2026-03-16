package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCountryRequest(
    @NotBlank(message = "Country name is required")
    String name,

    @NotBlank(message = "Country code is required")
    String code
) {}
