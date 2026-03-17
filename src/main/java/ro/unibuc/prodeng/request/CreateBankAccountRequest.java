package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record CreateBankAccountRequest(
    @NotBlank(message = "Currency code is required")
    String currencyCode,

    @NotBlank(message = "Country code is required")
    String countryCode,

    @NotBlank(message = "Account holder name is required")
    String accountHolderName
) {}
