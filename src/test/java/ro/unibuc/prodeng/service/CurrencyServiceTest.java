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

    @Test
    void getCurrencyById_missing_throwsEntityNotFound() {
        when(currencyRepository.findById("1")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyById("1"));
    }

    @Test
    void getCurrencyByCode_existing_returnsResponse_uppercasesCode() {
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        CurrencyResponse result = currencyService.getCurrencyByCode("eur");

        assertEquals("EUR", result.code());
        verify(currencyRepository).findByCode("EUR");
    }

    @Test
    void getCurrencyByCode_missing_throwsEntityNotFound() {
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyByCode("eur"));
    }

    @Test
    void getCurrencyEntityByCode_existing_returnsEntity() {
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        CurrencyEntity result = currencyService.getCurrencyEntityByCode("eur");

        assertEquals("EUR", result.code());
        verify(currencyRepository).findByCode("EUR");
    }

    @Test
    void getCurrencyEntityByCode_missing_throwsEntityNotFound() {
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyEntityByCode("eur"));
    }

    @Test
    void createCurrency_whenCodeAlreadyExists_throwsIllegalArgument() {
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "eur");
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> currencyService.createCurrency(request));
        verify(currencyRepository).existsByCode("EUR");
        verifyNoMoreInteractions(currencyRepository);
    }

    @Test
    void createCurrency_whenNew_savesAndReturnsResponse() {
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "eur");
        when(currencyRepository.existsByCode("EUR")).thenReturn(false);
        CurrencyEntity saved = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.save(any(CurrencyEntity.class))).thenReturn(saved);

        CurrencyResponse result = currencyService.createCurrency(request);

        assertEquals("1", result.id());
        assertEquals("EUR", result.code());
        verify(currencyRepository).existsByCode("EUR");
        verify(currencyRepository).save(any(CurrencyEntity.class));
    }
}
