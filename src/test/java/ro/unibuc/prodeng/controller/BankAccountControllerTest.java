package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.request.CreateBankAccountRequest;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.BalanceSheetResponse;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.BankAccountService;
import ro.unibuc.prodeng.service.ReportingService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
class BankAccountControllerTest {

    @Mock
    private BankAccountService bankAccountService;

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private BankAccountController bankAccountController;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private final BankAccountResponse testAccount1 = new BankAccountResponse(
            "acc-1", "RO49AAAA1234567890123456", "user-1", "EUR", "RO", "John Doe", new BigDecimal("1000.0"), false);
    private final BankAccountResponse testAccount2 = new BankAccountResponse(
            "acc-2", "GB29NWBK60161331926819", "user-2", "GBP", "GB", "Jane Smith", new BigDecimal("500.0"), false);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bankAccountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Set up SecurityContext for balance-sheet authorization checks
        UserDetails principal = UserDetails.builder()
                .id("user-1")
                .username("john")
                .email("john@example.com")
                .password("secret")
                .authorities(List.of())
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testTransferEndpoint_returnsCreatedWithTransactions() throws Exception {
        // Arrange
        TransactionResponse debit = new TransactionResponse(
                "tx-1",
                "acc-1",
                "DEBIT",
                new BigDecimal("100.00"),
                "Transfer",
                Instant.now()
        );

        TransactionResponse credit = new TransactionResponse(
                "tx-2",
                "acc-2",
                "CREDIT",
                new BigDecimal("100.00"),
                "Transfer",
                Instant.now()
        );

        when(bankAccountService.transfer(any(CreateTransferRequest.class)))
                .thenReturn(List.of(debit, credit));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Transfer"
        );

        // Act & Assert
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].accountId").value("acc-1"))
                .andExpect(jsonPath("$[0].type").value("DEBIT"))
                .andExpect(jsonPath("$[1].accountId").value("acc-2"))
                .andExpect(jsonPath("$[1].type").value("CREDIT"));
    }

    @Test
    void testTransferEndpoint_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        // Arrange
        when(bankAccountService.transfer(any(CreateTransferRequest.class)))
                .thenThrow(new IllegalArgumentException("Insufficient funds in source account"));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("200.00"),
                "Too much"
        );

        // Act & Assert
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient funds in source account"));
    }

    @Test
    void testTransferEndpoint_whenServiceThrowsEntityNotFound_returnsNotFound() throws Exception {
        // Arrange
        when(bankAccountService.transfer(any(CreateTransferRequest.class)))
                .thenThrow(new EntityNotFoundException("acc-1"));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Missing source"
        );

        // Act & Assert
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    // --- POST /api/accounts ---

    @Test
    void testCreateAccount_validRequest_returnsCreated() throws Exception {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("EUR", "RO", "John Doe");
        when(bankAccountService.createAccount(any(CreateBankAccountRequest.class))).thenReturn(testAccount1);

        // Act & Assert
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("acc-1")))
                .andExpect(jsonPath("$.iban", is("RO49AAAA1234567890123456")))
                .andExpect(jsonPath("$.currencyCode", is("EUR")));
    }

    @Test
    void testCreateAccount_unsupportedCurrency_returnsBadRequest() throws Exception {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("XYZ", "RO", "John Doe");
        when(bankAccountService.createAccount(any())).thenThrow(new IllegalArgumentException("Unsupported currency: XYZ"));

        // Act & Assert
        mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unsupported currency")));
    }

    // --- GET /api/accounts/me ---

    @Test
    void testGetMyAccounts_withAccounts_returnsOk() throws Exception {
        // Arrange
        when(bankAccountService.getMyAccounts()).thenReturn(List.of(testAccount1));

        // Act & Assert
        mockMvc.perform(get("/api/accounts/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("acc-1")));
    }

    @Test
    void testGetMyAccounts_noAccounts_returnsEmptyList() throws Exception {
        // Arrange
        when(bankAccountService.getMyAccounts()).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/accounts/me").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- GET /api/accounts (paginated, admin) ---

    @Test
    void testGetAllAccounts_withPagination_returnsOk() throws Exception {
        // Arrange
        Page<BankAccountResponse> page = new PageImpl<>(List.of(testAccount1), PageRequest.of(0, 10), 1);
        when(bankAccountService.getAllAccounts(any())).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/accounts")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is("acc-1")));
    }

    // --- GET /api/accounts/{id} ---

    @Test
    void testGetAccountById_existing_returnsOk() throws Exception {
        // Arrange
        when(bankAccountService.getAccountById("acc-1")).thenReturn(testAccount1);

        // Act & Assert
        mockMvc.perform(get("/api/accounts/{id}", "acc-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("acc-1")))
                .andExpect(jsonPath("$.iban", is("RO49AAAA1234567890123456")));
    }

    @Test
    void testGetAccountById_nonExisting_returnsNotFound() throws Exception {
        // Arrange
        when(bankAccountService.getAccountById("999")).thenThrow(new EntityNotFoundException("Account"));

        // Act & Assert
        mockMvc.perform(get("/api/accounts/{id}", "999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/accounts/by-iban ---

    @Test
    void testGetAccountByIban_existing_returnsOk() throws Exception {
        // Arrange
        when(bankAccountService.getAccountByIban("RO49AAAA1234567890123456")).thenReturn(testAccount1);

        // Act & Assert
        mockMvc.perform(get("/api/accounts/by-iban")
                .param("iban", "RO49AAAA1234567890123456")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is("RO49AAAA1234567890123456")));
    }

    @Test
    void testGetAccountByIban_nonExisting_returnsNotFound() throws Exception {
        // Arrange
        when(bankAccountService.getAccountByIban("INVALID")).thenThrow(new EntityNotFoundException("Account"));

        // Act & Assert
        mockMvc.perform(get("/api/accounts/by-iban")
                .param("iban", "INVALID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/accounts/user/{userId} ---

    @Test
    void testGetAccountsByUserId_withAccounts_returnsOk() throws Exception {
        // Arrange
        when(bankAccountService.getAccountsByUserId("user-1")).thenReturn(List.of(testAccount1));

        // Act & Assert
        mockMvc.perform(get("/api/accounts/user/{userId}", "user-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is("user-1")));
    }

    @Test
    void testGetAccountsByUserId_noAccounts_returnsEmptyList() throws Exception {
        // Arrange
        when(bankAccountService.getAccountsByUserId("user-x")).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/accounts/user/{userId}", "user-x").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- DELETE /api/accounts/{id} ---

    @Test
    void testCloseAccount_existing_returnsNoContent() throws Exception {
        // Arrange
        doNothing().when(bankAccountService).closeAccount("acc-1");

        // Act & Assert
        mockMvc.perform(delete("/api/accounts/{id}", "acc-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(bankAccountService).closeAccount("acc-1");
    }

    @Test
    void testCloseAccount_nonExisting_returnsNotFound() throws Exception {
        // Arrange
        doThrow(new EntityNotFoundException("Account")).when(bankAccountService).closeAccount("999");

        // Act & Assert
        mockMvc.perform(delete("/api/accounts/{id}", "999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCloseAccount_alreadyClosed_returnsBadRequest() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("Account is already closed")).when(bankAccountService).closeAccount("acc-1");

        // Act & Assert
        mockMvc.perform(delete("/api/accounts/{id}", "acc-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Account is already closed")));
    }

    // --- GET /api/accounts/{id}/balance-sheet ---

    @Test
    void testGetBalanceSheet_existingAccount_returnsOk() throws Exception {
        // Arrange
        BankAccountEntity accountEntity = BankAccountEntity.builder()
                .id("acc-1").userId("user-1").build();
        when(bankAccountService.getEntityById("acc-1")).thenReturn(accountEntity);
        BalanceSheetResponse balanceSheet = new BalanceSheetResponse(
                "acc-1", "John Doe", "EUR", BigDecimal.valueOf(1000), Collections.emptyList());
        when(reportingService.getBalanceSheet(eq("acc-1"), any(), any())).thenReturn(balanceSheet);

        // Act & Assert
        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", "acc-1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is("acc-1")))
                .andExpect(jsonPath("$.accountName", is("John Doe")))
                .andExpect(jsonPath("$.currency", is("EUR")));
    }

    @Test
    void testGetBalanceSheet_nonExistingAccount_returnsNotFound() throws Exception {
        // Arrange
        when(bankAccountService.getEntityById("999"))
                .thenThrow(new EntityNotFoundException("Account"));

        // Act & Assert
        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", "999").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
