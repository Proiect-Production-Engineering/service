package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "currency_exchange_rates")
public record CurrencyExchangeRateEntity(
    @Id
    String id,
    String sourceCurrency,
    String targetCurrency,
    Double exchangeRate
) {}
