package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "currency_exchange_rates")
@CompoundIndex(name = "source_target_idx", def = "{'sourceCurrency': 1, 'targetCurrency': 1}", unique = true)
public record CurrencyExchangeRateEntity(
    @Id
    String id,
    String sourceCurrency,
    String targetCurrency,
    BigDecimal exchangeRate
) {}
