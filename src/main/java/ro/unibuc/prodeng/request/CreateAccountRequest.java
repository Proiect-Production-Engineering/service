package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
    @NotBlank(message = "User ID is required")
    String userId,

    @NotBlank(message = "Account name is required")
    String accountName,

    @NotBlank(message = "Currency is required")
    String currency
) {}
