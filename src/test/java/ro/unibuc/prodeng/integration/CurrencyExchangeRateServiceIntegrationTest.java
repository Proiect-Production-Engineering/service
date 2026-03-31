package ro.unibuc.prodeng.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ro.unibuc.prodeng.integration.IntegrationTestBase;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;
import ro.unibuc.prodeng.service.CurrencyExchangeRateService;

class CurrencyExchangeRateServiceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CurrencyExchangeRateService exchangeRateService;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private CurrencyExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void cleanUp() {
        // Arrange
        exchangeRateRepository.deleteAll();
        currencyRepository.deleteAll();
    }

    @Test
    void setAndGetExchangeRate_persistsForwardAndInverseRates() {
        // Arrange
        currencyRepository.save(new CurrencyEntity(null, "Euro", "EUR"));
        currencyRepository.save(new CurrencyEntity(null, "Romanian Leu", "RON"));

        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", new BigDecimal("4.5"));

        // Act
        ExchangeRateResponse created = exchangeRateService.setExchangeRate(request);
        ExchangeRateResponse forward = exchangeRateService.getExchangeRate("EUR", "RON");
        ExchangeRateResponse inverse = exchangeRateService.getExchangeRate("RON", "EUR");

        // Assert
        assertNotNull(created.id());
        assertEquals("EUR", forward.sourceCurrency());
        assertEquals("RON", forward.targetCurrency());
        assertEquals(0, new BigDecimal("4.5").compareTo(forward.exchangeRate()));

        BigDecimal expectedInverse = BigDecimal.ONE.divide(new BigDecimal("4.5"), 6, RoundingMode.HALF_UP);
        assertEquals("RON", inverse.sourceCurrency());
        assertEquals("EUR", inverse.targetCurrency());
        assertEquals(0, expectedInverse.compareTo(inverse.exchangeRate()));

        List<CurrencyExchangeRateEntity> storedRates = exchangeRateRepository.findAll();
        assertEquals(2, storedRates.size());
    }
}
