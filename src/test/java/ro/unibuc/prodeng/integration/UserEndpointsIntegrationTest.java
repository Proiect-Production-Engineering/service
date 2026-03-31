package ro.unibuc.prodeng.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import ro.unibuc.prodeng.integration.IntegrationTestBase;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.ChangeNameRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for user management endpoints (/api/users, /api/auth).
 * Uses Testcontainers MongoDB — see {@link ro.unibuc.prodeng.IntegrationTestBase}.
 */
@DisplayName("User endpoints integration tests (IT)")
class UserEndpointsIntegrationTest extends IntegrationTestBase {

    /** Prefix used in usernames created by this test class to enable targeted cleanup. */
    private static final String IT_USERNAME_PREFIX = "itusr_";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BankAccountRepository bankAccountRepository;

    // ------------------------------------------------------------------ setup
    @BeforeEach
    void cleanTestData() {
        // Remove accounts created by test users
        mongoTemplate.remove(
                Query.query(Criteria.where("userId").in(
                        userRepository.findAll().stream()
                                .filter(u -> u.getUsername().startsWith(IT_USERNAME_PREFIX))
                                .map(UserEntity::getId)
                                .toList())),
                BankAccountEntity.class);
        // Remove test users
        mongoTemplate.remove(
                Query.query(Criteria.where("username").regex("^" + IT_USERNAME_PREFIX)),
                UserEntity.class);
    }

    // ========================================== AUTH — sign up / sign in ======

    @Test
    void signUp_validUser_returns200WithJwt() throws Exception {
        SignUpRequest body = new SignUpRequest(IT_USERNAME_PREFIX + "signup1",
                "signup1@it.test", "password1234");

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        String jwt = result.getResponse().getContentAsString().trim();
        assertFalse(jwt.isBlank(), "JWT should not be blank");
    }

    @Test
    void signUp_duplicateUsername_returns400() throws Exception {
        String username = IT_USERNAME_PREFIX + "dup_uname";
        signUpUser(username, "dup1@it.test", "password1234");

        SignUpRequest dup = new SignUpRequest(username, "dup2@it.test", "password1234");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUp_duplicateEmail_returns400() throws Exception {
        String email = "dup_email@it.test";
        signUpUser(IT_USERNAME_PREFIX + "email1", email, "password1234");

        SignUpRequest dup = new SignUpRequest(IT_USERNAME_PREFIX + "email2", email, "password1234");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signUp_reservedAdminUsername_returns400() throws Exception {
        SignUpRequest body = new SignUpRequest("admin", "notadmin@it.test", "password1234");
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signIn_validCredentials_returns200WithJwt() throws Exception {
        signUpUser(IT_USERNAME_PREFIX + "signin1", "signin1@it.test", "password1234");

        SignInRequest body = new SignInRequest(IT_USERNAME_PREFIX + "signin1", "password1234");
        MvcResult result = mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().trim().isBlank());
    }

    @Test
    void signIn_wrongPassword_returns401() throws Exception {
        signUpUser(IT_USERNAME_PREFIX + "badpw", "badpw@it.test", "password1234");

        SignInRequest body = new SignInRequest(IT_USERNAME_PREFIX + "badpw", "wrongpassword");
        mockMvc.perform(post("/api/auth/signin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ================================ GET /api/users/me (authenticated user) ==

    @Test
    void getMe_withValidJwt_returnsCurrentUser() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "me1", "me1@it.test", "password1234");

        mockMvc.perform(get("/api/users/me")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(IT_USERNAME_PREFIX + "me1")))
                .andExpect(jsonPath("$.email", is("me1@it.test")))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_USER")));
    }

    @Test
    void getMe_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ======================================= ADMIN — list / get / create =====

    @Test
    void getAllUsers_asAdmin_returnsUserList() throws Exception {
        String adminJwt = signInAsAdmin();

        mockMvc.perform(get("/api/users")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    void getAllUsers_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "noadmin1", "noadmin1@it.test", "password1234");

        mockMvc.perform(get("/api/users")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserById_asAdmin_returnsUser() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "getbyid", "getbyid@it.test", "password1234");
        String userId = extractUserId(jwt);

        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/users/{id}", userId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId)))
                .andExpect(jsonPath("$.username", is(IT_USERNAME_PREFIX + "getbyid")));
    }

    @Test
    void getUserById_nonExistent_returns404() throws Exception {
        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/users/{id}", "000000000000000000000000")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_asAdmin_returns201() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateUserRequest body = new CreateUserRequest(
                IT_USERNAME_PREFIX + "created1", "Created User", "created1@it.test", "password1234");

        mockMvc.perform(post("/api/users")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is(IT_USERNAME_PREFIX + "created1")))
                .andExpect(jsonPath("$.name", is("Created User")))
                .andExpect(jsonPath("$.email", is("created1@it.test")));
    }

    @Test
    void createUser_duplicateUsername_returns400() throws Exception {
        signUpUser(IT_USERNAME_PREFIX + "dup_cr", "dupcr1@it.test", "password1234");

        String adminJwt = signInAsAdmin();
        CreateUserRequest body = new CreateUserRequest(
                IT_USERNAME_PREFIX + "dup_cr", "Dupe", "dupcr2@it.test", "password1234");

        mockMvc.perform(post("/api/users")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ======================================= ADMIN — update name =============

    @Test
    void updateUserName_asAdmin_returns200WithUpdatedName() throws Exception {
        String jwt = signUpUser(IT_USERNAME_PREFIX + "rename", "rename@it.test", "password1234");
        String userId = extractUserId(jwt);

        String adminJwt = signInAsAdmin();
        ChangeNameRequest body = new ChangeNameRequest("Renamed User");

        mockMvc.perform(put("/api/users/{id}", userId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed User")));
    }

    @Test
    void updateUserName_adminAccount_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        String adminId = extractUserId(adminJwt);
        ChangeNameRequest body = new ChangeNameRequest("Hacked Admin");

        mockMvc.perform(put("/api/users/{id}", adminId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ======================================= ADMIN — delete (cascade) ========

    @Test
    void deleteUser_asAdmin_returns204AndCascadesSoftDelete() throws Exception {
        // Create a user and give them a bank account
        String jwt = signUpUser(IT_USERNAME_PREFIX + "delme", "delme@it.test", "password1234");
        String userId = extractUserId(jwt);

        BankAccountEntity account = BankAccountEntity.builder()
                .iban("ITDEL0000000000000001")
                .userId(userId)
                .currencyCode("EUR")
                .countryCode("RO")
                .accountHolderName("IT Del User")
                .balance(BigDecimal.ZERO)
                .deleted(false)
                .build();
        bankAccountRepository.save(account);

        String adminJwt = signInAsAdmin();
        mockMvc.perform(delete("/api/users/{id}", userId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNoContent());

        // Verify user deleted from repository
        assertFalse(userRepository.findById(userId).isPresent());

        // Verify bank account was cascade soft-deleted
        BankAccountEntity closedAccount = bankAccountRepository.findByIban("ITDEL0000000000000001")
                .orElseThrow();
        assertTrue(closedAccount.isDeleted());
    }

    @Test
    void deleteUser_adminAccount_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        String adminId = extractUserId(adminJwt);

        mockMvc.perform(delete("/api/users/{id}", adminId)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteUser_nonExistent_returns404() throws Exception {
        String adminJwt = signInAsAdmin();
        mockMvc.perform(delete("/api/users/{id}", "000000000000000000000000")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    // ======================================= ADMIN — get by email ============

    @Test
    void getUserByEmail_asAdmin_returnsUser() throws Exception {
        signUpUser(IT_USERNAME_PREFIX + "byemail", "byemail@it.test", "password1234");

        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/users/by-email")
                        .param("email", "byemail@it.test")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is(IT_USERNAME_PREFIX + "byemail")));
    }

    @Test
    void getUserByEmail_notFound_returns404() throws Exception {
        String adminJwt = signInAsAdmin();
        mockMvc.perform(get("/api/users/by-email")
                        .param("email", "noone@it.test")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
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

    /** Signs up a new user and returns the JWT. */
    private String signUpUser(String username, String email, String password) throws Exception {
        SignUpRequest body = new SignUpRequest(username, email, password);
        return mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().trim();
    }

    /** Calls GET /api/users/me to extract the user ID from the JWT. */
    private String extractUserId(String jwt) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asText();
    }
}
