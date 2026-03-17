package ro.unibuc.prodeng.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.response.CurrencyResponse;

@ExtendWith(SpringExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyService currencyService;

    @Test
    void getAllCurrencies_returnsMappedResponses() {
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        CurrencyEntity ron = new CurrencyEntity("2", "Romanian Leu", "RON");
        when(currencyRepository.findAll()).thenReturn(List.of(eur, ron));

        List<CurrencyResponse> result = currencyService.getAllCurrencies();

        assertEquals(2, result.size());
        assertEquals("Euro", result.get(0).name());
        assertEquals("RON", result.get(1).code());
    }

    @Test
    void getCurrencyById_existing_returnsResponse() {
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findById("1")).thenReturn(Optional.of(eur));

        CurrencyResponse result = currencyService.getCurrencyById("1");

        assertEquals("1", result.id());
        assertEquals("EUR", result.code());
    }
}
