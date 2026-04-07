package ro.unibuc.prodeng.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.CountryRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateBankAccountRequest;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for bank-account endpoints (/api/accounts).
 * Seeds countries &amp; currencies in {@code @BeforeEach} because Testcontainers
 * gives us a bare MongoDB (init-mongo.js does not run).
 */
@DisplayName("Bank account endpoints integration tests (IT)")
class BankAccountEndpointsIntegrationTest extends IntegrationTestBase {

    private static final String IT_USERNAME_PREFIX = "itbank_";
    private static final String IT_IBAN_PREFIX     = "ITBANK";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BankAccountRepository bankAccountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CountryRepository countryRepository;
    @Autowired private CurrencyRepository currencyRepository;

    // ------------------------------------------------------------------ setup

    @BeforeEach
    void seedReferenceDataAndClean() {
        // Seed currencies if missing (Testcontainers starts a bare DB)
        if (!currencyRepository.existsByCode("EUR")) {
            currencyRepository.save(new CurrencyEntity(null, "Euro", "EUR"));
        }
        if (!currencyRepository.existsByCode("RON")) {
            currencyRepository.save(new CurrencyEntity(null, "Romanian new leu", "RON"));
        }
        if (!currencyRepository.existsByCode("GBP")) {
            currencyRepository.save(new CurrencyEntity(null, "British pound sterling", "GBP"));
        }

        // Seed countries if missing
        if (countryRepository.findByCode("RO").isEmpty()) {
            countryRepository.save(new CountryEntity(null, "Romania", "RO", "aaaacccccccccccccccc"));
        }
        if (countryRepository.findByCode("GB").isEmpty()) {
            countryRepository.save(new CountryEntity(null, "United Kingdom", "GB", "aaaannnnnnnnnnnnnn"));
        }

        // Clean test-specific data using targeted queries instead of loading entire collections
        mongoTemplate.remove(
                Query.query(Criteria.where("iban").regex("^" + IT_IBAN_PREFIX)),
                BankAccountEntity.class);

        // Find test user IDs with a targeted query
        var testUserIds = mongoTemplate.find(
                Query.query(Criteria.where("username").regex("^" + IT_USERNAME_PREFIX)),
                UserEntity.class
        ).stream().map(UserEntity::getId).toList();

        if (!testUserIds.isEmpty()) {
            // Find account IDs belonging to test users with a targeted query
            var testAccountIds = mongoTemplate.find(
                    Query.query(Criteria.where("userId").in(testUserIds)),
                    BankAccountEntity.class
            ).stream().map(BankAccountEntity::getId).toList();

            if (!testAccountIds.isEmpty()) {
                mongoTemplate.remove(
                        Query.query(Criteria.where("accountId").in(testAccountIds)),
                        TransactionEntity.class);
            }
            mongoTemplate.remove(
                    Query.query(Criteria.where("userId").in(testUserIds)),
                    BankAccountEntity.class);
        }
        mongoTemplate.remove(
                Query.query(Criteria.where("username").regex("^" + IT_USERNAME_PREFIX)),
                UserEntity.class);
    }

    // ==================================== CREATE ACCOUNT =====================

    @Test
    void createAccount_authenticated_returns201() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "create1", "create1@itbank.test");

        CreateBankAccountRequest body = new CreateBankAccountRequest("EUR", "RO", "Create Test Holder");
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currencyCode", is("EUR")))
                .andExpect(jsonPath("$.countryCode", is("RO")))
                .andExpect(jsonPath("$.accountHolderName", is("Create Test Holder")))
                .andExpect(jsonPath("$.balance").value(comparesEqualTo(0)))
                .andExpect(jsonPath("$.iban", not(emptyOrNullString())))
                .andExpect(jsonPath("$.deleted", is(false)));
    }

    @Test
    void createAccount_withoutAuth_returns401() throws Exception {
        CreateBankAccountRequest body = new CreateBankAccountRequest("EUR", "RO", "No Auth");
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAccount_unsupportedCurrency_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "badcur", "badcur@itbank.test");

        CreateBankAccountRequest body = new CreateBankAccountRequest("XYZ", "RO", "Bad Currency");
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAccount_duplicateCurrencyForSameUser_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "dupcur", "dupcur@itbank.test");

        CreateBankAccountRequest body = new CreateBankAccountRequest("EUR", "RO", "Dup Currency");
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // Second account with same currency
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createAccount_maxAccountsExceeded_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "maxacc", "maxacc@itbank.test");

        // Seed a 4th currency so we can attempt creating a 4th account
        if (!currencyRepository.existsByCode("USD")) {
            currencyRepository.save(new CurrencyEntity(null, "US Dollar", "USD"));
        }

        // Create 3 accounts (max)
        for (String currency : new String[]{"EUR", "RON", "GBP"}) {
            CreateBankAccountRequest body = new CreateBankAccountRequest(currency, "RO", "Max Test");
            mockMvc.perform(post("/api/accounts")
                            .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());
        }

        // 4th account should fail with max-accounts error
        CreateBankAccountRequest body = new CreateBankAccountRequest("USD", "RO", "Exceeded");
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Maximum number of accounts")));
    }

    // ==================================== GET MY ACCOUNTS ====================

    @Test
    void getMyAccounts_returnsOnlyOwnActiveAccounts() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "myacc", "myacc@itbank.test");

        // Create one account
        CreateBankAccountRequest body = new CreateBankAccountRequest("RON", "RO", "My Acc Holder");
        mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/accounts/me")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountHolderName", is("My Acc Holder")));
    }

    @Test
    void getMyAccounts_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================== ADMIN — list / get =================

    @Test
    void getAllAccounts_asAdmin_returnsPagedResults() throws Exception {
        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/accounts")
                        .param("page", "0").param("size", "10")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllAccounts_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "noadm1", "noadm1@itbank.test");
        mockMvc.perform(get("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAccountById_asAdmin_returnsAccount() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "getid", "getid@itbank.test");
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "Get By ID");

        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(accountId)))
                .andExpect(jsonPath("$.accountHolderName", is("Get By ID")));
    }

    @Test
    void getAccountByIban_asAdmin_returnsAccount() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "getiban", "getiban@itbank.test");
        String accountId = createAccountAndGetId(jwt, "GBP", "GB", "Get By IBAN");

        // Find the IBAN from the account
        String adminJwt = signInAsAdmin();
        MvcResult accResult = mockMvc.perform(get("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andReturn();
        String iban = objectMapper.readTree(accResult.getResponse().getContentAsString())
                .get("iban").asText();

        mockMvc.perform(get("/api/accounts/by-iban")
                        .param("iban", iban)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is(iban)));
    }

    @Test
    void getAccountsByUserId_asAdmin_includesDeletedAccounts() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "byusr", "byusr@itbank.test");
        String userId = extractUserId(jwt);

        // Create an account and then soft-delete it directly
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "Soft Del Holder");
        BankAccountEntity entity = bankAccountRepository.findById(accountId).orElseThrow();
        entity.setDeleted(true);
        bankAccountRepository.save(entity);

        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/accounts/user/{userId}", userId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].deleted", is(true)));
    }

    // ==================================== CLOSE ACCOUNT ======================

    @Test
    void closeAccount_zeroBalance_returns204() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "close1", "close1@itbank.test");
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "Close Me");

        String adminJwt = signInAsAdmin();
        mockMvc.perform(delete("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNoContent());

        // Verify soft-deleted in DB
        BankAccountEntity closed = bankAccountRepository.findById(accountId).orElseThrow();
        assertTrue(closed.isDeleted());
    }

    @Test
    void closeAccount_nonZeroBalance_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "close2", "close2@itbank.test");
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "Has Balance");

        // Seed a non-zero balance directly (simulates a funded account)
        BankAccountEntity entity = bankAccountRepository.findById(accountId).orElseThrow();
        entity.setBalance(new BigDecimal("100.00"));
        bankAccountRepository.save(entity);

        String adminJwt = signInAsAdmin();
        mockMvc.perform(delete("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("balance must be zero")));
    }

    @Test
    void closeAccount_alreadyClosed_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "close3", "close3@itbank.test");
        String accountId = createAccountAndGetId(jwt, "RON", "RO", "Already Closed");

        String adminJwt = signInAsAdmin();
        // Close it once
        mockMvc.perform(delete("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNoContent());
        // Close it again
        mockMvc.perform(delete("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeAccount_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "close4", "close4@itbank.test");
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "No Perms");

        mockMvc.perform(delete("/api/accounts/{id}", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isForbidden());
    }

    // ==================================== TRANSFER ===========================

    @Test
    void transfer_sameCurrency_returns201AndUpdatesBalances() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "xfer1", "xfer1@itbank.test");
        String userId = extractUserId(jwt);

        // Create source and target accounts
        String sourceId = createAccountAndGetId(jwt, "EUR", "RO", "Source Holder");
        String targetId = createAccountAndGetId(jwt, "RON", "RO", "Target Holder");

        // Both must have same currency — create a second user for the target with EUR
        String jwt2 = signUpUser(IT_USERNAME_PREFIX + "xfer2", "xfer2@itbank.test");
        String targetId2 = createAccountAndGetId(jwt2, "EUR", "RO", "Target EUR Holder");

        // Fund source account directly
        BankAccountEntity source = bankAccountRepository.findById(sourceId).orElseThrow();
        source.setBalance(new BigDecimal("1000.00"));
        bankAccountRepository.save(source);

        CreateTransferRequest body = new CreateTransferRequest(
                sourceId, targetId2, new BigDecimal("250.00"), "IT transfer test");

        mockMvc.perform(post("/api/accounts/transfer")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type", is("DEBIT")))
                .andExpect(jsonPath("$[1].type", is("CREDIT")));

        // Verify balances updated in DB
        BigDecimal srcBalance = bankAccountRepository.findById(sourceId).orElseThrow().getBalance();
        BigDecimal tgtBalance = bankAccountRepository.findById(targetId2).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("750.00").compareTo(srcBalance));
        assertEquals(0, new BigDecimal("250.00").compareTo(tgtBalance));
    }

    @Test
    void transfer_insufficientFunds_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "xferfail", "xferfail@itbank.test");
        String sourceId = createAccountAndGetId(jwt, "EUR", "RO", "Empty Src");

        String jwt2 = signUpUser(IT_USERNAME_PREFIX + "xferfail2", "xferfail2@itbank.test");
        String targetId = createAccountAndGetId(jwt2, "EUR", "RO", "Target");

        // Source has zero balance
        CreateTransferRequest body = new CreateTransferRequest(
                sourceId, targetId, new BigDecimal("100.00"), "Should fail");

        mockMvc.perform(post("/api/accounts/transfer")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Insufficient funds")));
    }

    @Test
    void transfer_differentCurrency_returns400() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "xfercur", "xfercur@itbank.test");
        String eurId = createAccountAndGetId(jwt, "EUR", "RO", "EUR Holder");
        String ronId = createAccountAndGetId(jwt, "RON", "RO", "RON Holder");

        // Fund source
        BankAccountEntity src = bankAccountRepository.findById(eurId).orElseThrow();
        src.setBalance(new BigDecimal("500.00"));
        bankAccountRepository.save(src);

        CreateTransferRequest body = new CreateTransferRequest(
                eurId, ronId, new BigDecimal("100.00"), "Cross currency");

        mockMvc.perform(post("/api/accounts/transfer")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("same currency")));
    }

    @Test
    void transfer_notOwner_returns400() throws Exception {
        // User A creates account
        String jwtA = signUpUser(IT_USERNAME_PREFIX + "xferownA", "xferownA@itbank.test");
        String accA = createAccountAndGetId(jwtA, "EUR", "RO", "Owner A");
        BankAccountEntity entityA = bankAccountRepository.findById(accA).orElseThrow();
        entityA.setBalance(new BigDecimal("500.00"));
        bankAccountRepository.save(entityA);

        // User B creates account and tries to transfer FROM user A's account
        String jwtB = signUpUser(IT_USERNAME_PREFIX + "xferownB", "xferownB@itbank.test");
        String accB = createAccountAndGetId(jwtB, "EUR", "RO", "Owner B");

        CreateTransferRequest body = new CreateTransferRequest(
                accA, accB, new BigDecimal("100.00"), "Stolen transfer");

        mockMvc.perform(post("/api/accounts/transfer")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwtB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("does not belong")));
    }

    @Test
    void transfer_withoutAuth_returns401() throws Exception {
        CreateTransferRequest body = new CreateTransferRequest(
                "x", "y", new BigDecimal("10.00"), "no auth");
        mockMvc.perform(post("/api/accounts/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ==================================== BALANCE SHEET ======================

    @Test
    void getBalanceSheet_accountOwner_returns200() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "bsheet", "bsheet@itbank.test");
        String accountId = createAccountAndGetId(jwt, "EUR", "RO", "Balance Sheet Holder");

        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId)))
                .andExpect(jsonPath("$.accountName", is("Balance Sheet Holder")))
                .andExpect(jsonPath("$.currency", is("EUR")));
    }

    @Test
    void getBalanceSheet_otherUser_returns403() throws Exception {
        String jwt1 = signUpUser(IT_USERNAME_PREFIX + "bs_own", "bsown@itbank.test");
        String accountId = createAccountAndGetId(jwt1, "EUR", "RO", "Owner's Account");

        String jwt2 = signUpUser(IT_USERNAME_PREFIX + "bs_other", "bsother@itbank.test");
        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt2))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBalanceSheet_asAdmin_returns200() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "bs_adm", "bsadm@itbank.test");
        String accountId = createAccountAndGetId(jwt, "RON", "RO", "Admin View Account");

        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", accountId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(accountId)));
    }

    @Test
    void getBalanceSheet_nonExistentAccount_returns404() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "bs404", "bs404@itbank.test");
        mockMvc.perform(get("/api/accounts/{id}/balance-sheet", "000000000000000000000000")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isNotFound());
    }

    // ================================================================= helpers

    private String signInAsAdmin() throws Exception {
        SignInRequest body = new SignInRequest("admin", "test-admin-password");
        return mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().trim();
    }

    private String signUpUser(String username, String email) throws Exception {
        SignUpRequest body = new SignUpRequest(username, email, "password1234");
        return mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().trim();
    }

    private String extractUserId(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Creates an account via the API and returns its ID. */
    private String createAccountAndGetId(String jwt, String currency, String country, String holder) throws Exception {
        CreateBankAccountRequest body = new CreateBankAccountRequest(currency, country, holder);
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header(AuthenticationTokenFilter.HEADER_TITLE, AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }

}
