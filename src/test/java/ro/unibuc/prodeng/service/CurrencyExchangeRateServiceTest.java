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
}
