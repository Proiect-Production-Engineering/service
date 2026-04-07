package ro.unibuc.prodeng.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;
import ro.unibuc.prodeng.service.CurrencyExchangeRateService;

class CurrencyExchangeRateServiceIntegrationTest extends IntegrationTestBase {

    private static final String IT_CUR_PREFIX = "ITSVC";

    @Autowired
    private CurrencyExchangeRateService exchangeRateService;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private CurrencyExchangeRateRepository exchangeRateRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanUp() {
        // Clean only test-specific data, not all data
        mongoTemplate.remove(
                Query.query(new Criteria().orOperator(
                        Criteria.where("sourceCurrency").regex("^" + IT_CUR_PREFIX),
                        Criteria.where("targetCurrency").regex("^" + IT_CUR_PREFIX))),
                CurrencyExchangeRateEntity.class);
        mongoTemplate.remove(
                Query.query(Criteria.where("code").regex("^" + IT_CUR_PREFIX)),
                CurrencyEntity.class);
    }

    @Test
    void setAndGetExchangeRate_persistsForwardAndInverseRates() {
        // Arrange
        currencyRepository.save(new CurrencyEntity(null, "IT Euro", IT_CUR_PREFIX + "EUR"));
        currencyRepository.save(new CurrencyEntity(null, "IT Romanian Leu", IT_CUR_PREFIX + "RON"));

        SetExchangeRateRequest request = new SetExchangeRateRequest(IT_CUR_PREFIX + "EUR", IT_CUR_PREFIX + "RON", new BigDecimal("4.5"));

        // Act
        ExchangeRateResponse created = exchangeRateService.setExchangeRate(request);
        ExchangeRateResponse forward = exchangeRateService.getExchangeRate(IT_CUR_PREFIX + "EUR", IT_CUR_PREFIX + "RON");
        ExchangeRateResponse inverse = exchangeRateService.getExchangeRate(IT_CUR_PREFIX + "RON", IT_CUR_PREFIX + "EUR");

        // Assert
        assertNotNull(created.id());
        assertEquals(IT_CUR_PREFIX + "EUR", forward.sourceCurrency());
        assertEquals(IT_CUR_PREFIX + "RON", forward.targetCurrency());
        assertEquals(0, new BigDecimal("4.5").compareTo(forward.exchangeRate()));

        BigDecimal expectedInverse = BigDecimal.ONE.divide(new BigDecimal("4.5"), 6, RoundingMode.HALF_UP);
        assertEquals(IT_CUR_PREFIX + "RON", inverse.sourceCurrency());
        assertEquals(IT_CUR_PREFIX + "EUR", inverse.targetCurrency());
        assertEquals(0, expectedInverse.compareTo(inverse.exchangeRate()));

        List<CurrencyExchangeRateEntity> storedRates = mongoTemplate.find(
                Query.query(Criteria.where("sourceCurrency").regex("^" + IT_CUR_PREFIX)),
                CurrencyExchangeRateEntity.class);
        assertEquals(2, storedRates.size());
    }
}
