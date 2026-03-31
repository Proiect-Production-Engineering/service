package ro.unibuc.prodeng.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.CountryRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateBankAccountRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for balance sheet (owner vs admin vs forbidden), using real MongoDB.
 */
@DisplayName("Balance sheet integration tests (IT)")
class BalanceSheetIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "test-admin-password";
    private static final String IT_USER_PREFIX = "itbs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        seedCurrencyAndCountryIfNeeded();
        transactionRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userRepository.deleteAll(userRepository.findAll().stream()
                .filter(u -> u.getUsername() != null && u.getUsername().startsWith(IT_USER_PREFIX))
                .toList());
    }

    private void seedCurrencyAndCountryIfNeeded() {
        if (!currencyRepository.existsByCode("RON")) {
            currencyRepository.save(new CurrencyEntity(null, "Romanian leu", "RON"));
        }
        if (!countryRepository.existsByCode("RO")) {
            countryRepository.save(new CountryEntity(null, "Romania", "RO", "aaaacccccccccccccccc"));
        }
    }

    @Test
    void balanceSheet_ownerGets200AndRunningBalance() throws Exception {
        String u = IT_USER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String jwt = signUp(u, "Password123!");

        String accountId = createAccount(jwt, new CreateBankAccountRequest("RON", "RO", "IT Holder"));

        TransactionEntity tx = new TransactionEntity(
                null,
                accountId,
                TransactionType.CREDIT,
                new BigDecimal("100.00"),
                "IT-BS-CREDIT",
                Instant.parse("2025-04-01T12:00:00Z"));
        transactionRepository.save(tx);

        mockMvc.perform(get("/api/accounts/" + accountId + "/balance-sheet")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId)))
                .andExpect(jsonPath("$.currency", is("RON")))
                .andExpect(jsonPath("$.entries", hasSize(1)))
                .andExpect(jsonPath("$.entries[0].description", is("IT-BS-CREDIT")))
                .andExpect(jsonPath("$.entries[0].runningBalance").exists());
    }

    @Test
    void balanceSheet_otherUserGets403() throws Exception {
        String owner = IT_USER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String ownerJwt = signUp(owner, "Password123!");
        String accountId = createAccount(ownerJwt, new CreateBankAccountRequest("RON", "RO", "Owner"));

        String other = IT_USER_PREFIX + "o" + UUID.randomUUID().toString().substring(0, 7);
        String otherJwt = signUp(other, "Password123!");

        mockMvc.perform(get("/api/accounts/" + accountId + "/balance-sheet")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + otherJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void balanceSheet_adminCanAccessAnyAccount() throws Exception {
        String owner = IT_USER_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String ownerJwt = signUp(owner, "Password123!");
        String accountId = createAccount(ownerJwt, new CreateBankAccountRequest("RON", "RO", "Owner2"));

        transactionRepository.save(new TransactionEntity(
                null,
                accountId,
                TransactionType.CREDIT,
                new BigDecimal("10.00"),
                "IT-BS-ADMIN",
                Instant.parse("2025-04-02T12:00:00Z")));

        String adminJwt = signInAdmin();

        mockMvc.perform(get("/api/accounts/" + accountId + "/balance-sheet")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId)))
                .andExpect(jsonPath("$.entries", hasSize(1)));
    }

    private String signUp(String username, String password) throws Exception {
        SignUpRequest req = new SignUpRequest(username, username + "@it.example.com", password);
        return mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .trim();
    }

    private String signInAdmin() throws Exception {
        SignInRequest signIn = new SignInRequest("admin", ADMIN_PASSWORD);
        return mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signIn)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .trim();
    }

    private String createAccount(String jwt, CreateBankAccountRequest request) throws Exception {
        String json = mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asText();
    }
}
