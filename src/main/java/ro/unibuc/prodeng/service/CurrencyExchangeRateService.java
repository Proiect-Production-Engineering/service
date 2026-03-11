package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;

@Service
public class CurrencyExchangeRateService {

    @Autowired
    private CurrencyExchangeRateRepository exchangeRateRepository;

    // ISO 4217 currency codes - major world currencies
    private static final String[] CURRENCIES = {
        // Americas
        "USD", // United States Dollar
        "CAD", // Canadian Dollar
        "MXN", // Mexican Peso
        "BRL", // Brazilian Real
        "ARS", // Argentine Peso
        "CLP", // Chilean Peso
        "COP", // Colombian Peso
        "PEN", // Peruvian Sol
        
        // Europe
        "EUR", // Euro
        "GBP", // British Pound
        "CHF", // Swiss Franc
        "SEK", // Swedish Krona
        "NOK", // Norwegian Krone
        "DKK", // Danish Krone
        "PLN", // Polish Zloty
        "CZK", // Czech Koruna
        "HUF", // Hungarian Forint
        "RON", // Romanian Leu
        "BGN", // Bulgarian Lev
        "HRK", // Croatian Kuna
        "RSD", // Serbian Dinar
        "UAH", // Ukrainian Hryvnia
        "TRY", // Turkish Lira
        "RUB", // Russian Ruble
        "ISK", // Icelandic Krona
        
        // Asia-Pacific
        "JPY", // Japanese Yen
        "CNY", // Chinese Yuan
        "HKD", // Hong Kong Dollar
        "SGD", // Singapore Dollar
        "KRW", // South Korean Won
        "TWD", // Taiwan Dollar
        "INR", // Indian Rupee
        "IDR", // Indonesian Rupiah
        "MYR", // Malaysian Ringgit
        "PHP", // Philippine Peso
        "THB", // Thai Baht
        "VND", // Vietnamese Dong
        "PKR", // Pakistani Rupee
        "BDT", // Bangladeshi Taka
        "LKR", // Sri Lankan Rupee
        "AUD", // Australian Dollar
        "NZD", // New Zealand Dollar
        
        // Middle East
        "SAR", // Saudi Riyal
        "AED", // UAE Dirham
        "QAR", // Qatari Riyal
        "KWD", // Kuwaiti Dinar
        "BHD", // Bahraini Dinar
        "OMR", // Omani Rial
        "JOD", // Jordanian Dinar
        "ILS", // Israeli Shekel
        "EGP", // Egyptian Pound
        "IQD", // Iraqi Dinar
        "IRR", // Iranian Rial
        "LBP", // Lebanese Pound
        
        // Africa
        "ZAR", // South African Rand
        "NGN", // Nigerian Naira
        "KES", // Kenyan Shilling
        "GHS", // Ghanaian Cedi
        "ETB", // Ethiopian Birr
        "TZS", // Tanzanian Shilling
        "UGX", // Ugandan Shilling
        "MAD", // Moroccan Dirham
        "TND", // Tunisian Dinar
        "DZD", // Algerian Dinar
        "AOA", // Angolan Kwanza
        "XAF", // Central African CFA Franc
        "XOF", // West African CFA Franc
        
        // Other
        "NIO"  // Nicaraguan Córdoba
    };

    private static final Set<String> SUPPORTED_CURRENCIES = new HashSet<>(List.of(CURRENCIES));
    private static final BigDecimal MIN_RATE = new BigDecimal("0.0001");
    private static final BigDecimal MAX_RATE = new BigDecimal("10000");
    private static final int SCALE = 6; // 6 decimal places for exchange rates to balance precision and readability

    @PostConstruct
    public void initializeExchangeRates() {
        // Only initialize if database is empty
        if (exchangeRateRepository.count() == 0) {
            for (String sourceCurrency : CURRENCIES) {
                for (String targetCurrency : CURRENCIES) {
                    CurrencyExchangeRateEntity rate = new CurrencyExchangeRateEntity(
                            null,
                            sourceCurrency,
                            targetCurrency,
                            1.0
                    );
                    exchangeRateRepository.save(rate);
                }
            }
        }
    }

    public Map<String, Double> getAllExchangeRates() {
        List<CurrencyExchangeRateEntity> rates = exchangeRateRepository.findAll();
        Map<String, Double> exchangeMatrix = new HashMap<>();
        
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
                request.sourceCurrency(),
                request.targetCurrency(),
                sanitizedRate
        );

        // Keep inverse in sync so conversions remain coherent
        BigDecimal inverseRate = BigDecimal.ONE.divide(sanitizedRate, SCALE, RoundingMode.HALF_UP);
        upsertRate(request.targetCurrency(), request.sourceCurrency(), inverseRate);

        return toResponse(saved);
    }

    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        validateCurrencies(sourceCurrency, targetCurrency);

        CurrencyExchangeRateEntity rate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(sourceCurrency, targetCurrency)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange rate not found for " + sourceCurrency + " to " + targetCurrency));
        return toResponse(rate);
    }

    public BigDecimal convertAmount(BigDecimal amount, String sourceCurrency, String targetCurrency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        validateCurrencies(sourceCurrency, targetCurrency);

        if (sourceCurrency.equalsIgnoreCase(targetCurrency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        CurrencyExchangeRateEntity rate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(sourceCurrency, targetCurrency)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Exchange rate not found for " + sourceCurrency + " to " + targetCurrency));

        BigDecimal converted = amount.multiply(BigDecimal.valueOf(rate.exchangeRate()));
        return converted.setScale(2, RoundingMode.HALF_UP);
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
        if (!SUPPORTED_CURRENCIES.contains(sourceCurrency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported source currency: " + sourceCurrency);
        }
        if (!SUPPORTED_CURRENCIES.contains(targetCurrency.toUpperCase())) {
            throw new IllegalArgumentException("Unsupported target currency: " + targetCurrency);
        }
    }

    private BigDecimal sanitizeRate(Double rate) {
        if (rate == null) {
            throw new IllegalArgumentException("Exchange rate is required");
        }
        BigDecimal value = BigDecimal.valueOf(rate);
        if (value.compareTo(MIN_RATE) < 0 || value.compareTo(MAX_RATE) > 0) {
            throw new IllegalArgumentException("Exchange rate must be between " + MIN_RATE + " and " + MAX_RATE);
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private CurrencyExchangeRateEntity upsertRate(String sourceCurrency, String targetCurrency, BigDecimal rate) {
        CurrencyExchangeRateEntity existingRate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(sourceCurrency, targetCurrency)
                .orElse(null);

        CurrencyExchangeRateEntity rateToSave;
        if (existingRate != null) {
            rateToSave = new CurrencyExchangeRateEntity(
                    existingRate.id(),
                    sourceCurrency.toUpperCase(),
                    targetCurrency.toUpperCase(),
                    rate.doubleValue()
            );
        } else {
            rateToSave = new CurrencyExchangeRateEntity(
                    null,
                    sourceCurrency.toUpperCase(),
                    targetCurrency.toUpperCase(),
                    rate.doubleValue()
            );
        }

        return exchangeRateRepository.save(rateToSave);
    }
}
