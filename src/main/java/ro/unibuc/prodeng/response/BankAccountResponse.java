package ro.unibuc.prodeng.response;

public record BankAccountResponse(
    String id,
    String iban,
    String userId,
    String currencyCode,
    String countryCode,
    String accountHolderName,
    Double balance,
    boolean deleted
) {}
