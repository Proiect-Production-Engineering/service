package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyExchangeRateService {

    private final CurrencyExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;

    private static final BigDecimal MIN_RATE = new BigDecimal("0.0001");
    private static final BigDecimal MAX_RATE = new BigDecimal("10000");
    private static final int SCALE = 6;

    public Map<String, BigDecimal> getAllExchangeRates() {
        List<CurrencyExchangeRateEntity> rates = exchangeRateRepository.findAll();
        Map<String, BigDecimal> exchangeMatrix = new HashMap<>();

        for (CurrencyExchangeRateEntity rate : rates) {
            String key = rate.sourceCurrency() + "_" + rate.targetCurrency();
            exchangeMatrix.put(key, rate.exchangeRate());
        }

        return exchangeMatrix;
    }

    public ExchangeRateResponse setExchangeRate(SetExchangeRateRequest request) {
        validateCurrencies(request.sourceCurrency(), request.targetCurrency());

        BigDecimal sanitizedRate = sanitizeRate(request.exchangeRate());
        CurrencyExchangeRateEntity saved = upsertRate(
                request.sourceCurrency().toUpperCase(),
                request.targetCurrency().toUpperCase(),
                sanitizedRate
        );

        BigDecimal inverseRate = BigDecimal.ONE.divide(sanitizedRate, SCALE, RoundingMode.HALF_UP);
        upsertRate(request.targetCurrency().toUpperCase(), request.sourceCurrency().toUpperCase(), inverseRate);

        log.info("Exchange rate set: {} -> {} = {}", request.sourceCurrency(), request.targetCurrency(), sanitizedRate);
        return toResponse(saved);
    }

    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        validateCurrencies(sourceCurrency, targetCurrency);

        CurrencyExchangeRateEntity rate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(
                        sourceCurrency.toUpperCase(), targetCurrency.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange rate not found for " + sourceCurrency + " to " + targetCurrency));
        return toResponse(rate);
    }

    private ExchangeRateResponse toResponse(CurrencyExchangeRateEntity entity) {
        return new ExchangeRateResponse(
                entity.id(),
                entity.sourceCurrency(),
                entity.targetCurrency(),
                entity.exchangeRate()
        );
    }

    private void validateCurrencies(String sourceCurrency, String targetCurrency) {
        if (sourceCurrency == null || targetCurrency == null) {
            throw new IllegalArgumentException("Currency codes cannot be null");
        }
        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            throw new IllegalArgumentException("Source and target currencies must differ");
        }
        if (!currencyRepository.existsByCode(sourceCurrency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported source currency: " + sourceCurrency);
        }
        if (!currencyRepository.existsByCode(targetCurrency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported target currency: " + targetCurrency);
        }
    }

    private BigDecimal sanitizeRate(BigDecimal rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Exchange rate is required");
        }
        if (rate.compareTo(MIN_RATE) < 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException(
                    "Exchange rate must be between " + MIN_RATE + " and " + MAX_RATE);
        }
        return rate.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private CurrencyExchangeRateEntity upsertRate(String sourceCurrency, String targetCurrency, BigDecimal rate) {
        CurrencyExchangeRateEntity existing = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(sourceCurrency, targetCurrency)
                .orElse(null);

        CurrencyExchangeRateEntity rateToSave;
        if (existing != null) {
            rateToSave = new CurrencyExchangeRateEntity(
                    existing.id(), sourceCurrency, targetCurrency, rate);
        } else {
            rateToSave = new CurrencyExchangeRateEntity(
                    null, sourceCurrency, targetCurrency, rate);
        }

        return exchangeRateRepository.save(rateToSave);
    }
}
