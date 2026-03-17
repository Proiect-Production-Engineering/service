package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;

@ExtendWith(SpringExtension.class)
class CurrencyExchangeRateServiceTest {

    @Mock
    private CurrencyExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyExchangeRateService exchangeRateService;

    @Test
    void getAllExchangeRates_returnsExchangeMatrix() {
        CurrencyExchangeRateEntity eurRon = new CurrencyExchangeRateEntity("1", "EUR", "RON", 4.5);
        CurrencyExchangeRateEntity ronEur = new CurrencyExchangeRateEntity("2", "RON", "EUR", 0.22);
        when(exchangeRateRepository.findAll()).thenReturn(List.of(eurRon, ronEur));

        Map<String, Double> result = exchangeRateService.getAllExchangeRates();

        assertEquals(2, result.size());
        assertEquals(4.5, result.get("EUR_RON"));
        assertEquals(0.22, result.get("RON_EUR"));
    }

    @Test
    void setExchangeRate_newPair_createsForwardAndInverse() {
        SetExchangeRateRequest request = new SetExchangeRateRequest("eur", "ron", 4.5);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);
        when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrency(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(CurrencyExchangeRateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeRateResponse response = exchangeRateService.setExchangeRate(request);

        assertEquals("EUR", response.sourceCurrency());
        assertEquals("RON", response.targetCurrency());
        assertEquals(4.5, response.exchangeRate());
        verify(exchangeRateRepository, times(2)).save(any(CurrencyExchangeRateEntity.class));
    }

    @Test
    void setExchangeRate_existingPair_updatesForwardAndInverse() {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 5.0);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);

        CurrencyExchangeRateEntity existing = new CurrencyExchangeRateEntity("1", "EUR", "RON", 4.5);
        when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrency("EUR", "RON"))
                .thenReturn(Optional.of(existing));
        when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrency("RON", "EUR"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(CurrencyExchangeRateEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeRateResponse response = exchangeRateService.setExchangeRate(request);

        assertEquals("EUR", response.sourceCurrency());
        assertEquals("RON", response.targetCurrency());
        verify(exchangeRateRepository, times(2)).save(any(CurrencyExchangeRateEntity.class));
    }

    @Test
    void setExchangeRate_nullRate_throwsIllegalArgument() {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", null);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.setExchangeRate(request));
        verify(exchangeRateRepository, never()).save(any(CurrencyExchangeRateEntity.class));
    }

    @Test
    void setExchangeRate_rateTooLow_throwsIllegalArgument() {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 0.00001);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.setExchangeRate(request));
        verify(exchangeRateRepository, never()).save(any(CurrencyExchangeRateEntity.class));
    }

    @Test
    void setExchangeRate_rateTooHigh_throwsIllegalArgument() {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 20000.0);
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.setExchangeRate(request));
        verify(exchangeRateRepository, never()).save(any(CurrencyExchangeRateEntity.class));
    }

    @Test
    void getExchangeRate_existingRate_returnsResponse() {
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);

        CurrencyExchangeRateEntity rate = new CurrencyExchangeRateEntity("1", "EUR", "RON", 4.5);
        when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrency("EUR", "RON"))
                .thenReturn(Optional.of(rate));

        ExchangeRateResponse response = exchangeRateService.getExchangeRate("eur", "ron");

        assertEquals("EUR", response.sourceCurrency());
        assertEquals("RON", response.targetCurrency());
        assertEquals(4.5, response.exchangeRate());
    }

    @Test
    void getExchangeRate_missingRate_throwsIllegalArgument() {
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);
        when(currencyRepository.existsByCode("RON")).thenReturn(true);
        when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrency("EUR", "RON"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.getExchangeRate("eur", "ron"));
    }

    @Test
    void validateCurrencies_nullSource_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.getExchangeRate(null, "RON"));
    }

    @Test
    void validateCurrencies_nullTarget_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> exchangeRateService.getExchangeRate("EUR", null));
    }
}
