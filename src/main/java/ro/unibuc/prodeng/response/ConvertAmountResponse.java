package ro.unibuc.prodeng.response;

import java.math.BigDecimal;

public record ConvertAmountResponse(
    BigDecimal amount,
    String sourceCurrency,
    String targetCurrency,
    BigDecimal convertedAmount
) {}
