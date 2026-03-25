package ro.unibuc.prodeng.response;

import java.math.BigDecimal;

public record BankAccountResponse(
    String id,
    String iban,
    String userId,
    String currencyCode,
    String countryCode,
    String accountHolderName,
    BigDecimal balance,
    boolean deleted
) {}
