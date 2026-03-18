package ro.unibuc.prodeng.request;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionSearchRequest(
    String accountId,
    String type,
    String descriptionKeyword,
    Instant from,
    Instant to,
    String iban,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    Integer page,
    Integer size
) {}
