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
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.response.CurrencyResponse;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyService currencyService;

    @Test
    void getAllCurrencies_returnsMappedResponses() {
        // Arrange
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        CurrencyEntity ron = new CurrencyEntity("2", "Romanian Leu", "RON");
        when(currencyRepository.findAll()).thenReturn(List.of(eur, ron));

        // Act
        List<CurrencyResponse> result = currencyService.getAllCurrencies();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Euro", result.get(0).name());
        assertEquals("RON", result.get(1).code());
    }

    @Test
    void getCurrencyById_existing_returnsResponse() {
        // Arrange
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findById("1")).thenReturn(Optional.of(eur));

        // Act
        CurrencyResponse result = currencyService.getCurrencyById("1");

        // Assert
        assertEquals("1", result.id());
        assertEquals("EUR", result.code());
    }

    @Test
    void getCurrencyById_missing_throwsEntityNotFound() {
        // Arrange
        when(currencyRepository.findById("1")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyById("1"));
    }

    @Test
    void getCurrencyByCode_existing_returnsResponse_uppercasesCode() {
        // Arrange
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        // Act
        CurrencyResponse result = currencyService.getCurrencyByCode("eur");

        // Assert
        assertEquals("EUR", result.code());
        verify(currencyRepository).findByCode("EUR");
    }

    @Test
    void getCurrencyByCode_missing_throwsEntityNotFound() {
        // Arrange
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyByCode("eur"));
    }

    @Test
    void getCurrencyEntityByCode_existing_returnsEntity() {
        // Arrange
        CurrencyEntity eur = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.of(eur));

        // Act
        CurrencyEntity result = currencyService.getCurrencyEntityByCode("eur");

        // Assert
        assertEquals("EUR", result.code());
        verify(currencyRepository).findByCode("EUR");
    }

    @Test
    void getCurrencyEntityByCode_missing_throwsEntityNotFound() {
        // Arrange
        when(currencyRepository.findByCode("EUR")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> currencyService.getCurrencyEntityByCode("eur"));
    }

    @Test
    void createCurrency_whenCodeAlreadyExists_throwsIllegalArgument() {
        // Arrange
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "eur");
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> currencyService.createCurrency(request));
        verify(currencyRepository).existsByCode("EUR");
        verifyNoMoreInteractions(currencyRepository);
    }

    @Test
    void createCurrency_whenNew_savesAndReturnsResponse() {
        // Arrange
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "eur");
        when(currencyRepository.existsByCode("EUR")).thenReturn(false);
        CurrencyEntity saved = new CurrencyEntity("1", "Euro", "EUR");
        when(currencyRepository.save(any(CurrencyEntity.class))).thenReturn(saved);

        // Act
        CurrencyResponse result = currencyService.createCurrency(request);

        // Assert
        assertEquals("1", result.id());
        assertEquals("EUR", result.code());
        verify(currencyRepository).existsByCode("EUR");
        verify(currencyRepository).save(any(CurrencyEntity.class));
    }

    @Test
    void deleteCurrency_whenNotExisting_throwsEntityNotFound() {
        // Arrange
        when(currencyRepository.existsById("1")).thenReturn(false);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> currencyService.deleteCurrency("1"));
        verify(currencyRepository).existsById("1");
    }

    @Test
    void deleteCurrency_whenExisting_deletesById() {
        // Arrange
        when(currencyRepository.existsById("1")).thenReturn(true);

        // Act
        currencyService.deleteCurrency("1");

        // Assert
        verify(currencyRepository).existsById("1");
        verify(currencyRepository).deleteById("1");
    }

    @Test
    void existsByCode_delegatesToRepositoryWithUppercase() {
        // Arrange
        when(currencyRepository.existsByCode("EUR")).thenReturn(true);

        // Act
        boolean result = currencyService.existsByCode("eur");

        // Assert
        assertEquals(true, result);
        verify(currencyRepository).existsByCode("EUR");
        verifyNoMoreInteractions(currencyRepository);
    }
}
