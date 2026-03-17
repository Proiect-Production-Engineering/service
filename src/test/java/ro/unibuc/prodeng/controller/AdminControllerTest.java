package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.AdminService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testSearchTransactions_withNoFilters_returnsResults() throws Exception {
        // Arrange
        Instant now = Instant.now();
        TransactionResponse tx = new TransactionResponse("tx1", "acc1", "CREDIT", new BigDecimal("100.00"), "Deposit", now);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(List.of(tx));

        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("tx1")))
                .andExpect(jsonPath("$[0].accountId", is("acc1")))
                .andExpect(jsonPath("$[0].type", is("CREDIT")))
                .andExpect(jsonPath("$[0].description", is("Deposit")));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_noResults_returnsEmptyList() throws Exception {
        // Arrange
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(Collections.emptyList());

        TransactionSearchRequest request = new TransactionSearchRequest("nonexistent", null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_withAllFilters_delegatesToService() throws Exception {
        // Arrange
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", "DEBIT", "rent", from, to);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_multipleResults_returnsAll() throws Exception {
        // Arrange
        Instant t1 = Instant.parse("2025-01-15T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-16T14:00:00Z");
        List<TransactionResponse> transactions = List.of(
                new TransactionResponse("tx1", "acc1", "CREDIT", new BigDecimal("500.00"), "Salary", t1),
                new TransactionResponse("tx2", "acc1", "DEBIT", new BigDecimal("100.00"), "Rent", t2)
        );
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(transactions);

        TransactionSearchRequest request = new TransactionSearchRequest("acc1", null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("tx1")))
                .andExpect(jsonPath("$[0].type", is("CREDIT")))
                .andExpect(jsonPath("$[1].id", is("tx2")))
                .andExpect(jsonPath("$[1].type", is("DEBIT")));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_responseContainsAllTransactionFields() throws Exception {
        // Arrange
        Instant timestamp = Instant.parse("2025-06-15T14:30:00Z");
        TransactionResponse tx = new TransactionResponse("tx-abc", "acc-xyz", "DEBIT", new BigDecimal("75.50"), "Grocery shopping", timestamp);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(List.of(tx));

        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is("tx-abc")))
                .andExpect(jsonPath("$[0].accountId", is("acc-xyz")))
                .andExpect(jsonPath("$[0].type", is("DEBIT")))
                .andExpect(jsonPath("$[0].amount", is(75.50)))
                .andExpect(jsonPath("$[0].description", is("Grocery shopping")));
    }
}
