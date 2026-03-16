package ro.unibuc.prodeng.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceSheetEntry(
    String transactionId,
    Instant timestamp,
    String description,
    String type,
    BigDecimal amount,
    BigDecimal runningBalance
) {}
