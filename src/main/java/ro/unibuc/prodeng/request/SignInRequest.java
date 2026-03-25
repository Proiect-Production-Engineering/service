package ro.unibuc.prodeng.request;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
    @NotBlank(message = "Username is required")
    String username,

    @NotBlank(message = "Password is required")
    String password
) {}
