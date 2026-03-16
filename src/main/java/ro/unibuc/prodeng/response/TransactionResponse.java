package ro.unibuc.prodeng.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    String id,
    String accountId,
    String type,
    BigDecimal amount,
    String description,
    Instant timestamp
) {}
