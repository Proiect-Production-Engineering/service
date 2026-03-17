package ro.unibuc.prodeng.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;
import ro.unibuc.prodeng.service.CurrencyExchangeRateService;

@ExtendWith(SpringExtension.class)
class CurrencyExchangeRateControllerTest {

    @Mock
    private CurrencyExchangeRateService exchangeRateService;

    @InjectMocks
    private CurrencyExchangeRateController exchangeRateController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(exchangeRateController)
                .setControllerAdvice(new ro.unibuc.prodeng.exception.GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getAllExchangeRates_returnsMatrix() throws Exception {
        Map<String, Double> matrix = Map.of("EUR_RON", 4.5, "RON_EUR", 0.22);
        when(exchangeRateService.getAllExchangeRates()).thenReturn(matrix);

        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.EUR_RON", equalTo(4.5)))
                .andExpect(jsonPath("$.RON_EUR", equalTo(0.22)));
    }

    @Test
    void getExchangeRate_returnsRate() throws Exception {
        ExchangeRateResponse response = new ExchangeRateResponse("1", "EUR", "RON", 4.5);
        when(exchangeRateService.getExchangeRate("EUR", "RON")).thenReturn(response);

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", "EUR")
                        .param("target", "RON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency", equalTo("EUR")))
                .andExpect(jsonPath("$.targetCurrency", equalTo("RON")))
                .andExpect(jsonPath("$.exchangeRate", equalTo(4.5)));
    }

    @Test
    void getExchangeRate_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        when(exchangeRateService.getExchangeRate("EUR", "RON"))
                .thenThrow(new IllegalArgumentException("Exchange rate not found for EUR to RON"));

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", "EUR")
                        .param("target", "RON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Exchange rate not found for EUR to RON")));
    }

    @Test
    void setExchangeRate_success_returnsCreated() throws Exception {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 4.5);
        ExchangeRateResponse response = new ExchangeRateResponse("1", "EUR", "RON", 4.5);
        when(exchangeRateService.setExchangeRate(any(SetExchangeRateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("1")))
                .andExpect(jsonPath("$.exchangeRate", equalTo(4.5)));
    }

    @Test
    void setExchangeRate_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 4.5);
        when(exchangeRateService.setExchangeRate(any(SetExchangeRateRequest.class)))
                .thenThrow(new IllegalArgumentException("Unsupported currency"));

        mockMvc.perform(post("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Unsupported currency")));
    }

    @Test
    void updateExchangeRate_success_returnsOk() throws Exception {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 4.5);
        ExchangeRateResponse response = new ExchangeRateResponse("1", "EUR", "RON", 4.5);
        when(exchangeRateService.setExchangeRate(any(SetExchangeRateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("1")))
                .andExpect(jsonPath("$.exchangeRate", equalTo(4.5)));
    }

    @Test
    void updateExchangeRate_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        SetExchangeRateRequest request = new SetExchangeRateRequest("EUR", "RON", 4.5);
        when(exchangeRateService.setExchangeRate(any(SetExchangeRateRequest.class)))
                .thenThrow(new IllegalArgumentException("Unsupported currency"));

        mockMvc.perform(put("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Unsupported currency")));
    }
}
