package ro.unibuc.prodeng.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        CurrencyExchangeRateEntity existingRate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(request.sourceCurrency(), request.targetCurrency())
                .orElse(null);

        CurrencyExchangeRateEntity rateToSave;
        if (existingRate != null) {
            // Update existing rate
            rateToSave = new CurrencyExchangeRateEntity(
                    existingRate.id(),
                    request.sourceCurrency(),
                    request.targetCurrency(),
                    request.exchangeRate()
            );
        } else {
            // Create new rate
            rateToSave = new CurrencyExchangeRateEntity(
                    null,
                    request.sourceCurrency(),
                    request.targetCurrency(),
                    request.exchangeRate()
            );
        }

        CurrencyExchangeRateEntity saved = exchangeRateRepository.save(rateToSave);
        return toResponse(saved);
    }

    public ExchangeRateResponse getExchangeRate(String sourceCurrency, String targetCurrency) {
        CurrencyExchangeRateEntity rate = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrency(sourceCurrency, targetCurrency)
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
}
