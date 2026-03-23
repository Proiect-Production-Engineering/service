package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.request.CreateCountryRequest;
import ro.unibuc.prodeng.response.CountryResponse;
import ro.unibuc.prodeng.service.CountryService;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
class CountryControllerTest {

    @Mock
    private CountryService countryService;

    @InjectMocks
    private CountryController countryController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CountryResponse testCountry1 = new CountryResponse("1", "Romania", "RO", "aaaacccccccccccccccc");
    private final CountryResponse testCountry2 = new CountryResponse("2", "France", "FR", "nnnnnnnnnncccccccccccnn");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(countryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- GET /api/countries ---

    @Test
    void testGetAllCountries_withCountries_returnsOk() throws Exception {
        when(countryService.getAllCountries()).thenReturn(List.of(testCountry1, testCountry2));

        mockMvc.perform(get("/api/countries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Romania")))
                .andExpect(jsonPath("$[0].code", is("RO")))
                .andExpect(jsonPath("$[1].name", is("France")));
    }

    @Test
    void testGetAllCountries_noCountries_returnsEmptyList() throws Exception {
        when(countryService.getAllCountries()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/countries").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- GET /api/countries/{id} ---

    @Test
    void testGetCountryById_existing_returnsOk() throws Exception {
        when(countryService.getCountryById("1")).thenReturn(testCountry1);

        mockMvc.perform(get("/api/countries/{id}", "1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("Romania")))
                .andExpect(jsonPath("$.code", is("RO")));
    }

    @Test
    void testGetCountryById_nonExisting_returnsNotFound() throws Exception {
        when(countryService.getCountryById("999")).thenThrow(new EntityNotFoundException("Country"));

        mockMvc.perform(get("/api/countries/{id}", "999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/countries/by-code ---

    @Test
    void testGetCountryByCode_existing_returnsOk() throws Exception {
        when(countryService.getCountryByCode("RO")).thenReturn(testCountry1);

        mockMvc.perform(get("/api/countries/by-code").param("code", "RO").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("RO")))
                .andExpect(jsonPath("$.name", is("Romania")));
    }

    @Test
    void testGetCountryByCode_nonExisting_returnsNotFound() throws Exception {
        when(countryService.getCountryByCode("XX")).thenThrow(new EntityNotFoundException("Country"));

        mockMvc.perform(get("/api/countries/by-code").param("code", "XX").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/countries ---

    @Test
    void testCreateCountry_validRequest_returnsCreated() throws Exception {
        CreateCountryRequest request = new CreateCountryRequest("Romania", "RO", "aaaacccccccccccccccc");
        when(countryService.createCountry(any(CreateCountryRequest.class))).thenReturn(testCountry1);

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Romania")))
                .andExpect(jsonPath("$.code", is("RO")));
    }

    @Test
    void testCreateCountry_duplicateCode_returnsBadRequest() throws Exception {
        CreateCountryRequest request = new CreateCountryRequest("Romania", "RO", "aaaacccccccccccccccc");
        when(countryService.createCountry(any())).thenThrow(new IllegalArgumentException("Country with code already exists: RO"));

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("RO")));
    }

    // --- DELETE /api/countries/{id} ---

    @Test
    void testDeleteCountry_existing_returnsNoContent() throws Exception {
        doNothing().when(countryService).deleteCountry("1");

        mockMvc.perform(delete("/api/countries/{id}", "1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(countryService).deleteCountry("1");
    }

    @Test
    void testDeleteCountry_nonExisting_returnsNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Country")).when(countryService).deleteCountry("999");

        mockMvc.perform(delete("/api/countries/{id}", "999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
