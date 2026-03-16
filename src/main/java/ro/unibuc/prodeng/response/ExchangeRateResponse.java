package ro.unibuc.prodeng.response;

public record ExchangeRateResponse(
    String id,
    String sourceCurrency,
    String targetCurrency,
    Double exchangeRate
) {}
