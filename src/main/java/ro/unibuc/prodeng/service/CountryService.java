package ro.unibuc.prodeng.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.repository.CountryRepository;
import ro.unibuc.prodeng.request.CreateCountryRequest;
import ro.unibuc.prodeng.response.CountryResponse;

@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository countryRepository;

    public List<CountryResponse> getAllCountries() {
        return countryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CountryResponse getCountryById(String id) {
        CountryEntity country = countryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        return toResponse(country);
    }

    public CountryResponse getCountryByCode(String code) {
        CountryEntity country = countryRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(code));
        return toResponse(country);
    }

    public CountryResponse createCountry(CreateCountryRequest request) {
        String normalizedCode = request.code().toUpperCase();
        if (countryRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("Country with code already exists: " + normalizedCode);
        }

        CountryEntity country = new CountryEntity(null, request.name(), normalizedCode, request.ibanPattern());
        CountryEntity saved = countryRepository.save(country);
        return toResponse(saved);
    }

    public void deleteCountry(String id) {
        if (!countryRepository.existsById(id)) {
            throw new EntityNotFoundException(id);
        }
        countryRepository.deleteById(id);
    }

    public CountryEntity getCountryEntityByCode(String code) {
        return countryRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(code));
    }

    private CountryResponse toResponse(CountryEntity entity) {
        return new CountryResponse(entity.id(), entity.name(), entity.code(), entity.ibanPattern());
    }
}
