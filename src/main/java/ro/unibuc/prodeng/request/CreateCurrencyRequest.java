package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCurrencyRequest(
    @NotBlank(message = "Currency name is required")
    String name,

    @NotBlank(message = "Currency code is required")
    String code
) {}
