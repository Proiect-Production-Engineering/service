package ro.unibuc.prodeng.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConvertAmountRequest(
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    @NotBlank(message = "Source currency is required")
    String sourceCurrency,

    @NotBlank(message = "Target currency is required")
    String targetCurrency
) {}
