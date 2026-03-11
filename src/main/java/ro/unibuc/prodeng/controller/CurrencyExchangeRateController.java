package ro.unibuc.prodeng.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.request.ConvertAmountRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;
import ro.unibuc.prodeng.response.ConvertAmountResponse;
import ro.unibuc.prodeng.service.CurrencyExchangeRateService;

@RestController
@RequestMapping("/api/exchange-rates")
public class CurrencyExchangeRateController {

    @Autowired
    private CurrencyExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<Map<String, Double>> getAllExchangeRates() {
        Map<String, Double> exchangeMatrix = exchangeRateService.getAllExchangeRates();
        return ResponseEntity.ok(exchangeMatrix);
    }

    @GetMapping("/rate")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @RequestParam String source,
            @RequestParam String target) {
        ExchangeRateResponse rate = exchangeRateService.getExchangeRate(source, target);
        return ResponseEntity.ok(rate);
    }

    @PostMapping
    public ResponseEntity<ExchangeRateResponse> setExchangeRate(@Valid @RequestBody SetExchangeRateRequest request) {
        ExchangeRateResponse rate = exchangeRateService.setExchangeRate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rate);
    }

    @PutMapping
    public ResponseEntity<ExchangeRateResponse> updateExchangeRate(@Valid @RequestBody SetExchangeRateRequest request) {
        ExchangeRateResponse rate = exchangeRateService.setExchangeRate(request);
        return ResponseEntity.ok(rate);
    }

    @PostMapping("/convert")
    public ResponseEntity<ConvertAmountResponse> convertAmount(@Valid @RequestBody ConvertAmountRequest request) {
        BigDecimal converted = exchangeRateService.convertAmount(
                request.amount(),
                request.sourceCurrency(),
                request.targetCurrency()
        );

        ConvertAmountResponse response = new ConvertAmountResponse(
            request.amount().setScale(2, RoundingMode.HALF_UP),
                request.sourceCurrency(),
                request.targetCurrency(),
                converted
        );
        return ResponseEntity.ok(response);
    }
}
