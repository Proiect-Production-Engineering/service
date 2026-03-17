package ro.unibuc.prodeng.response;

import java.math.BigDecimal;
import java.util.List;

public record BalanceSheetResponse(
    String accountId,
    String accountName,
    String currency,
    BigDecimal currentBalance,
    List<BalanceSheetEntry> entries
) {}
