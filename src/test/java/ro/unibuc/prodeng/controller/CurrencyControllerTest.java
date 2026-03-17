package ro.unibuc.prodeng.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.response.CurrencyResponse;
import ro.unibuc.prodeng.service.CurrencyService;

@ExtendWith(SpringExtension.class)
class CurrencyControllerTest {

    @Mock
    private CurrencyService currencyService;

    @InjectMocks
    private CurrencyController currencyController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(currencyController)
                .setControllerAdvice(new ro.unibuc.prodeng.exception.GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getAllCurrencies_returnsList() throws Exception {
        List<CurrencyResponse> responses = List.of(
                new CurrencyResponse("1", "Euro", "EUR"),
                new CurrencyResponse("2", "Romanian Leu", "RON")
        );
        when(currencyService.getAllCurrencies()).thenReturn(responses);

        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code", equalTo("EUR")))
                .andExpect(jsonPath("$[1].code", equalTo("RON")));
    }

    @Test
    void getCurrencyById_existing_returnsCurrency() throws Exception {
        CurrencyResponse response = new CurrencyResponse("1", "Euro", "EUR");
        when(currencyService.getCurrencyById("1")).thenReturn(response);

        mockMvc.perform(get("/api/currencies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("1")))
                .andExpect(jsonPath("$.code", equalTo("EUR")));
    }

    @Test
    void getCurrencyById_missing_returnsNotFound() throws Exception {
        when(currencyService.getCurrencyById("1")).thenThrow(new EntityNotFoundException("1"));

        mockMvc.perform(get("/api/currencies/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getCurrencyByCode_existing_returnsCurrency() throws Exception {
        CurrencyResponse response = new CurrencyResponse("1", "Euro", "EUR");
        when(currencyService.getCurrencyByCode("EUR")).thenReturn(response);

        mockMvc.perform(get("/api/currencies/by-code").param("code", "EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", equalTo("EUR")));
    }

    @Test
    void getCurrencyByCode_missing_returnsNotFound() throws Exception {
        when(currencyService.getCurrencyByCode("EUR")).thenThrow(new EntityNotFoundException("EUR"));

        mockMvc.perform(get("/api/currencies/by-code").param("code", "EUR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createCurrency_success_returnsCreated() throws Exception {
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "EUR");
        CurrencyResponse response = new CurrencyResponse("1", "Euro", "EUR");
        when(currencyService.createCurrency(any(CreateCurrencyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/currencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("1")))
                .andExpect(jsonPath("$.code", equalTo("EUR")));
    }

    @Test
    void createCurrency_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        CreateCurrencyRequest request = new CreateCurrencyRequest("Euro", "EUR");
        when(currencyService.createCurrency(any(CreateCurrencyRequest.class)))
                .thenThrow(new IllegalArgumentException("Currency with code already exists: EUR"));

        mockMvc.perform(post("/api/currencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Currency with code already exists: EUR")));
    }
}
