package ro.unibuc.prodeng.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.response.CurrencyResponse;

@Service
public class CurrencyService {

    @Autowired
    private CurrencyRepository currencyRepository;

    public List<CurrencyResponse> getAllCurrencies() {
        return currencyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CurrencyResponse getCurrencyById(String id) {
        CurrencyEntity currency = currencyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        return toResponse(currency);
    }

    public CurrencyResponse getCurrencyByCode(String code) {
        CurrencyEntity currency = currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(code));
        return toResponse(currency);
    }

    public CurrencyEntity getCurrencyEntityByCode(String code) {
        return currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(code));
    }

    public CurrencyResponse createCurrency(CreateCurrencyRequest request) {
        String normalizedCode = request.code().toUpperCase();
        if (currencyRepository.existsByCode(normalizedCode)) {
            throw new IllegalArgumentException("Currency with code already exists: " + normalizedCode);
        }

        CurrencyEntity currency = new CurrencyEntity(null, request.name(), normalizedCode);
        CurrencyEntity saved = currencyRepository.save(currency);
        return toResponse(saved);
    }

    public void deleteCurrency(String id) {
        if (!currencyRepository.existsById(id)) {
            throw new EntityNotFoundException(id);
        }
        currencyRepository.deleteById(id);
    }

    public boolean existsByCode(String code) {
        return currencyRepository.existsByCode(code.toUpperCase());
    }

    private CurrencyResponse toResponse(CurrencyEntity entity) {
        return new CurrencyResponse(entity.id(), entity.name(), entity.code());
    }
}
