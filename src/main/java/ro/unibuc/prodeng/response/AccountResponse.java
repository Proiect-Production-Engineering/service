package ro.unibuc.prodeng.response;

public record AccountResponse(
    String id,
    String userId,
    String accountName,
    String currency
) {}
