package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransactionRequest(
    @NotBlank(message = "Account ID is required")
    String accountId,

    @NotBlank(message = "Transaction type is required (CREDIT or DEBIT)")
    String type,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount,

    String description
) {}
