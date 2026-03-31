package ro.unibuc.prodeng.integration;

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

import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.request.CreateCurrencyRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for currency endpoints ({@code /api/currencies}).
 */
@DisplayName("Currency endpoints integration tests (IT)")
class CurrencyEndpointsIntegrationTest extends IntegrationTestBase {

    private static final String IT_CURRENCY_PREFIX = "ITX";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private CurrencyRepository currencyRepository;

    @BeforeEach
    void cleanIntegrationData() {
        mongoTemplate.remove(
                Query.query(Criteria.where("code").regex("^" + IT_CURRENCY_PREFIX)),
                CurrencyEntity.class);
    }

    // ============================================================ GET /api/currencies

    @Test
    void getAllCurrencies_authenticated_returns200() throws Exception {
        seedCurrency(IT_CURRENCY_PREFIX + "A", "IT Currency A");
        String jwt = signUpUser("itcur_list", "itcur_list@test.com");

        mockMvc.perform(get("/api/currencies")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.code=='" + IT_CURRENCY_PREFIX + "A')]").exists());
    }

    @Test
    void getAllCurrencies_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================ GET /api/currencies/{id}

    @Test
    void getCurrencyById_found_returns200() throws Exception {
        String id = seedCurrency(IT_CURRENCY_PREFIX + "B", "IT Currency B");
        String jwt = signUpUser("itcur_byid", "itcur_byid@test.com");

        mockMvc.perform(get("/api/currencies/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(IT_CURRENCY_PREFIX + "B")))
                .andExpect(jsonPath("$.name", is("IT Currency B")));
    }

    @Test
    void getCurrencyById_notFound_returns404() throws Exception {
        String jwt = signUpUser("itcur_nf", "itcur_nf@test.com");

        mockMvc.perform(get("/api/currencies/{id}", "nonexistent-id-123")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isNotFound());
    }

    // ============================================================ GET /api/currencies/by-code

    @Test
    void getCurrencyByCode_found_returns200() throws Exception {
        seedCurrency(IT_CURRENCY_PREFIX + "C", "IT Currency C");
        String jwt = signUpUser("itcur_code", "itcur_code@test.com");

        mockMvc.perform(get("/api/currencies/by-code")
                        .param("code", IT_CURRENCY_PREFIX + "C")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(IT_CURRENCY_PREFIX + "C")));
    }

    @Test
    void getCurrencyByCode_notFound_returns404() throws Exception {
        String jwt = signUpUser("itcur_codenf", "itcur_codenf@test.com");

        mockMvc.perform(get("/api/currencies/by-code")
                        .param("code", "ZZZZZZ")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isNotFound());
    }

    // ============================================================ POST /api/currencies (admin)

    @Test
    void createCurrency_asAdmin_returns201() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateCurrencyRequest req = new CreateCurrencyRequest("IT Dollar", IT_CURRENCY_PREFIX + "D");

        mockMvc.perform(post("/api/currencies")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(IT_CURRENCY_PREFIX + "D")))
                .andExpect(jsonPath("$.name", is("IT Dollar")))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createCurrency_duplicateCode_returns400() throws Exception {
        seedCurrency(IT_CURRENCY_PREFIX + "E", "Original");
        String adminJwt = signInAsAdmin();
        CreateCurrencyRequest req = new CreateCurrencyRequest("Duplicate", IT_CURRENCY_PREFIX + "E");

        mockMvc.perform(post("/api/currencies")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCurrency_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser("itcur_noadm", "itcur_noadm@test.com");
        CreateCurrencyRequest req = new CreateCurrencyRequest("Blocked", IT_CURRENCY_PREFIX + "F");

        mockMvc.perform(post("/api/currencies")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCurrency_noAuth_returns401() throws Exception {
        CreateCurrencyRequest req = new CreateCurrencyRequest("NoAuth", IT_CURRENCY_PREFIX + "G");

        mockMvc.perform(post("/api/currencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCurrency_blankCode_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateCurrencyRequest req = new CreateCurrencyRequest("Bad", "");

        mockMvc.perform(post("/api/currencies")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ DELETE /api/currencies/{id} (admin)

    @Test
    void deleteCurrency_asAdmin_returns204() throws Exception {
        String id = seedCurrency(IT_CURRENCY_PREFIX + "H", "To Delete");
        String adminJwt = signInAsAdmin();

        mockMvc.perform(delete("/api/currencies/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNoContent());

        // verify gone
        mockMvc.perform(get("/api/currencies/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCurrency_notFound_returns404() throws Exception {
        String adminJwt = signInAsAdmin();

        mockMvc.perform(delete("/api/currencies/{id}", "nonexistent-id-999")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCurrency_asRegularUser_returns403() throws Exception {
        String id = seedCurrency(IT_CURRENCY_PREFIX + "I", "Protected");
        String jwt = signUpUser("itcur_delnoadm", "itcur_delnoadm@test.com");

        mockMvc.perform(delete("/api/currencies/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isForbidden());
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

    /** Seeds a currency directly in the DB and returns its ID. */
    private String seedCurrency(String code, String name) {
        CurrencyEntity entity = new CurrencyEntity(null, name, code);
        return currencyRepository.save(entity).id();
    }
}
