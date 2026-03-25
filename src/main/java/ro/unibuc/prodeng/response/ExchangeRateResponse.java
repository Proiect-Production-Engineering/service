package ro.unibuc.prodeng.response;

import java.math.BigDecimal;

public record ExchangeRateResponse(
    String id,
    String sourceCurrency,
    String targetCurrency,
    BigDecimal exchangeRate
) {}
