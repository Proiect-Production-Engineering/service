package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ro.unibuc.prodeng.request.AccountSearchRequest;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.AdminService;

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
        Page<TransactionResponse> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(page);

        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is("tx1")))
                .andExpect(jsonPath("$.content[0].accountId", is("acc1")))
                .andExpect(jsonPath("$.content[0].type", is("CREDIT")))
                .andExpect(jsonPath("$.content[0].description", is("Deposit")))
                .andExpect(jsonPath("$.totalElements", is(1)));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_noResults_returnsEmptyPage() throws Exception {
        // Arrange
        Page<TransactionResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(emptyPage);

        TransactionSearchRequest request = new TransactionSearchRequest("nonexistent", null, null, null, null, null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_withAllFilters_delegatesToService() throws Exception {
        // Arrange
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", "DEBIT", "rent", from, to, null, null, null, 0, 10);
        Page<TransactionResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

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
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 2);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(page);

        TransactionSearchRequest request = new TransactionSearchRequest("acc1", null, null, null, null, null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id", is("tx1")))
                .andExpect(jsonPath("$.content[0].type", is("CREDIT")))
                .andExpect(jsonPath("$.content[1].id", is("tx2")))
                .andExpect(jsonPath("$.content[1].type", is("DEBIT")))
                .andExpect(jsonPath("$.totalElements", is(2)));

        verify(adminService, times(1)).searchTransactions(any(TransactionSearchRequest.class));
    }

    @Test
    void testSearchTransactions_responseContainsAllTransactionFields() throws Exception {
        // Arrange
        Instant timestamp = Instant.parse("2025-06-15T14:30:00Z");
        TransactionResponse tx = new TransactionResponse("tx-abc", "acc-xyz", "DEBIT", new BigDecimal("75.50"), "Grocery shopping", timestamp);
        Page<TransactionResponse> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);
        when(adminService.searchTransactions(any(TransactionSearchRequest.class))).thenReturn(page);

        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/transactions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is("tx-abc")))
                .andExpect(jsonPath("$.content[0].accountId", is("acc-xyz")))
                .andExpect(jsonPath("$.content[0].type", is("DEBIT")))
                .andExpect(jsonPath("$.content[0].amount", is(75.50)))
                .andExpect(jsonPath("$.content[0].description", is("Grocery shopping")));
    }

    @Test
    void testSearchAccounts_returnsPagedResults() throws Exception {
        // Arrange
        BankAccountResponse account = new BankAccountResponse("acc1", "RO49AAAA1B31007593840000", "user1", "RON", "RO", "John Doe", new BigDecimal("1500.0"), false);
        Page<BankAccountResponse> page = new PageImpl<>(List.of(account), PageRequest.of(0, 20), 1);
        when(adminService.searchAccounts(any(AccountSearchRequest.class))).thenReturn(page);

        AccountSearchRequest request = new AccountSearchRequest(null, null, null, null);

        // Act & Assert
        mockMvc.perform(post("/api/admin/accounts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].iban", is("RO49AAAA1B31007593840000")))
                .andExpect(jsonPath("$.content[0].accountHolderName", is("John Doe")))
                .andExpect(jsonPath("$.totalElements", is(1)));

        verify(adminService, times(1)).searchAccounts(any(AccountSearchRequest.class));
    }

    @Test
    void testSearchAccounts_emptyResults_returnsEmptyPage() throws Exception {
        // Arrange
        Page<BankAccountResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(adminService.searchAccounts(any(AccountSearchRequest.class))).thenReturn(emptyPage);

        AccountSearchRequest request = new AccountSearchRequest("NONEXISTENT", null, 0, 10);

        // Act & Assert
        mockMvc.perform(post("/api/admin/accounts/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }
}
