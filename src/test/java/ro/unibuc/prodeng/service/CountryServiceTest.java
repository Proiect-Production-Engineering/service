package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.repository.CountryRepository;
import ro.unibuc.prodeng.request.CreateCountryRequest;
import ro.unibuc.prodeng.response.CountryResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class CountryServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private CountryService countryService;

    // --- getAllCountries ---

    @Test
    void testGetAllCountries_withMultipleCountries_returnsAll() {
        // Arrange
        List<CountryEntity> countries = List.of(
                new CountryEntity("1", "Romania", "RO", "aaaacccccccccccccccc"),
                new CountryEntity("2", "France", "FR", "nnnnnnnnnncccccccccccnn")
        );
        when(countryRepository.findAll()).thenReturn(countries);

        // Act
        List<CountryResponse> result = countryService.getAllCountries();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Romania", result.get(0).name());
        assertEquals("RO", result.get(0).code());
        assertEquals("France", result.get(1).name());
    }

    @Test
    void testGetAllCountries_withNoCountries_returnsEmptyList() {
        // Arrange
        when(countryRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<CountryResponse> result = countryService.getAllCountries();

        // Assert
        assertTrue(result.isEmpty());
    }

    // --- getCountryById ---

    @Test
    void testGetCountryById_existingCountry_returnsCountry() {
        // Arrange
        CountryEntity entity = new CountryEntity("1", "Romania", "RO", "aaaacccccccccccccccc");
        when(countryRepository.findById("1")).thenReturn(Optional.of(entity));

        // Act
        CountryResponse result = countryService.getCountryById("1");

        // Assert
        assertEquals("1", result.id());
        assertEquals("Romania", result.name());
        assertEquals("RO", result.code());
        assertEquals("aaaacccccccccccccccc", result.ibanPattern());
    }

    @Test
    void testGetCountryById_nonExistingCountry_throwsEntityNotFoundException() {
        // Arrange
        when(countryRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> countryService.getCountryById("999"));
    }

    // --- getCountryByCode ---

    @Test
    void testGetCountryByCode_existingCode_returnsCountry() {
        // Arrange
        CountryEntity entity = new CountryEntity("1", "Romania", "RO", "aaaacccccccccccccccc");
        when(countryRepository.findByCode("RO")).thenReturn(Optional.of(entity));

        // Act
        CountryResponse result = countryService.getCountryByCode("RO");

        // Assert
        assertEquals("RO", result.code());
        assertEquals("Romania", result.name());
    }

    @Test
    void testGetCountryByCode_lowercaseCode_normalizesToUpperCase() {
        // Arrange
        CountryEntity entity = new CountryEntity("1", "Romania", "RO", "aaaacccccccccccccccc");
        when(countryRepository.findByCode("RO")).thenReturn(Optional.of(entity));

        // Act
        CountryResponse result = countryService.getCountryByCode("ro");

        // Assert
        assertEquals("RO", result.code());
    }

    @Test
    void testGetCountryByCode_nonExistingCode_throwsEntityNotFoundException() {
        // Arrange
        when(countryRepository.findByCode("XX")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> countryService.getCountryByCode("XX"));
    }

    // --- createCountry ---

    @Test
    void testCreateCountry_validRequest_createsAndReturnsCountry() {
        // Arrange
        CreateCountryRequest request = new CreateCountryRequest("Romania", "ro", "aaaacccccccccccccccc");
        when(countryRepository.existsByCode("RO")).thenReturn(false);
        when(countryRepository.save(any(CountryEntity.class))).thenAnswer(invocation -> {
            CountryEntity entity = invocation.getArgument(0);
            return new CountryEntity("gen-id", entity.name(), entity.code(), entity.ibanPattern());
        });

        // Act
        CountryResponse result = countryService.createCountry(request);

        // Assert
        assertNotNull(result);
        assertEquals("gen-id", result.id());
        assertEquals("Romania", result.name());
        assertEquals("RO", result.code());
        verify(countryRepository, times(1)).save(any(CountryEntity.class));
    }

    @Test
    void testCreateCountry_duplicateCode_throwsIllegalArgumentException() {
        // Arrange
        CreateCountryRequest request = new CreateCountryRequest("Romania", "ro", "aaaacccccccccccccccc");
        when(countryRepository.existsByCode("RO")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> countryService.createCountry(request));
        assertTrue(ex.getMessage().contains("RO"));
        verify(countryRepository, never()).save(any());
    }

    // --- deleteCountry ---

    @Test
    void testDeleteCountry_existingId_deletesSuccessfully() {
        // Arrange
        when(countryRepository.existsById("1")).thenReturn(true);

        // Act
        countryService.deleteCountry("1");

        // Assert
        verify(countryRepository, times(1)).deleteById("1");
    }

    @Test
    void testDeleteCountry_nonExistingId_throwsEntityNotFoundException() {
        // Arrange
        when(countryRepository.existsById("999")).thenReturn(false);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> countryService.deleteCountry("999"));
        verify(countryRepository, never()).deleteById(any());
    }

    // --- getCountryEntityByCode ---

    @Test
    void testGetCountryEntityByCode_existingCode_returnsEntity() {
        // Arrange
        CountryEntity entity = new CountryEntity("1", "Romania", "RO", "aaaacccccccccccccccc");
        when(countryRepository.findByCode("RO")).thenReturn(Optional.of(entity));

        // Act
        CountryEntity result = countryService.getCountryEntityByCode("ro");

        // Assert
        assertEquals("1", result.id());
        assertEquals("Romania", result.name());
    }

    @Test
    void testGetCountryEntityByCode_nonExistingCode_throwsEntityNotFoundException() {
        // Arrange
        when(countryRepository.findByCode("XX")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> countryService.getCountryEntityByCode("xx"));
    }
}
