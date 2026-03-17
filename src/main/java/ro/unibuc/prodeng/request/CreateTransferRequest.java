package ro.unibuc.prodeng.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateTransferRequest(
    @NotBlank(message = "Source account ID is required")
    String sourceAccountId,

    @NotBlank(message = "Target account ID is required")
    String targetAccountId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    String description
) {}
