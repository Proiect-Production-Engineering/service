package ro.unibuc.prodeng.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ro.unibuc.prodeng.IntegrationTestBase;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.AccountSearchRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for admin transaction/account search (MongoDB via Testcontainers).
 * Isolated package so other team members can add their own *IntegrationTest classes without overlap.
 */
@TestPropertySource(properties = {
        "prodeng.adminPassword=ItAdminPassword123!Secure"
})
@DisplayName("Admin API integration tests (IT)")
class AdminEndpointsIntegrationTest extends IntegrationTestBase {

    private static final String IT_IBAN_PREFIX = "IT99TEST";
    private static final String IT_TX_DESCRIPTION = "IT-ADMIN-INTEGRATION-TEST";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanIntegrationData() {
        mongoTemplate.remove(Query.query(Criteria.where("iban").regex("^" + IT_IBAN_PREFIX)), BankAccountEntity.class);
        mongoTemplate.remove(Query.query(Criteria.where("description").is(IT_TX_DESCRIPTION)), TransactionEntity.class);
    }

    @Test
    void searchTransactions_withoutAuth_returns401() throws Exception {
        TransactionSearchRequest body = new TransactionSearchRequest(
                null, null, null, null, null, null, null, null, null, null);
        mockMvc.perform(post("/api/admin/transactions/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchAccounts_withoutAuth_returns401() throws Exception {
        AccountSearchRequest body = new AccountSearchRequest(null, null, null, null);
        mockMvc.perform(post("/api/admin/accounts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchAccounts_withAdminJwt_returnsSeededAccount() throws Exception {
        UserEntity admin = userRepository.findByUsername("admin").orElseThrow();
        BankAccountEntity account = BankAccountEntity.builder()
                .iban(IT_IBAN_PREFIX + "0000000000000001")
                .userId(admin.getId())
                .currencyCode("RON")
                .countryCode("RO")
                .accountHolderName("IT Integration Holder")
                .balance(new BigDecimal("1000.00"))
                .deleted(false)
                .build();
        bankAccountRepository.save(account);

        String jwt = signInAsAdmin();

        AccountSearchRequest body = new AccountSearchRequest(IT_IBAN_PREFIX, null, 0, 20);
        mockMvc.perform(post("/api/admin/accounts/search")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].iban", is(IT_IBAN_PREFIX + "0000000000000001")))
                .andExpect(jsonPath("$.content[0].accountHolderName", is("IT Integration Holder")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void searchTransactions_withAdminJwt_returnsSeededTransaction() throws Exception {
        UserEntity admin = userRepository.findByUsername("admin").orElseThrow();
        BankAccountEntity account = BankAccountEntity.builder()
                .iban(IT_IBAN_PREFIX + "0000000000000002")
                .userId(admin.getId())
                .currencyCode("EUR")
                .countryCode("RO")
                .accountHolderName("IT Tx Holder")
                .balance(new BigDecimal("500.00"))
                .deleted(false)
                .build();
        account = bankAccountRepository.save(account);

        TransactionEntity tx = new TransactionEntity(
                null,
                account.getId(),
                TransactionEntity.TransactionType.CREDIT,
                new BigDecimal("50.00"),
                IT_TX_DESCRIPTION,
                Instant.parse("2025-03-15T10:00:00Z"));
        transactionRepository.save(tx);

        String jwt = signInAsAdmin();

        TransactionSearchRequest body = new TransactionSearchRequest(
                account.getId(), null, null, null, null, null, null, null, 0, 20);

        mockMvc.perform(post("/api/admin/transactions/search")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].description", is(IT_TX_DESCRIPTION)))
                .andExpect(jsonPath("$.content[0].type", is("CREDIT")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    private String signInAsAdmin() throws Exception {
        SignInRequest signIn = new SignInRequest("admin", "ItAdminPassword123!Secure");
        String response = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signIn)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return response.trim();
    }
}
