package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SetExchangeRateRequest(
    @NotBlank(message = "Source currency is required")
    String sourceCurrency,

    @NotBlank(message = "Target currency is required")
    String targetCurrency,

    @NotNull(message = "Exchange rate is required")
    @Positive(message = "Exchange rate must be positive")
    Double exchangeRate
) {}
