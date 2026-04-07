package ro.unibuc.prodeng.integration;

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

import ro.unibuc.prodeng.model.CurrencyEntity;
import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.CurrencyExchangeRateRepository;
import ro.unibuc.prodeng.repository.CurrencyRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.SetExchangeRateRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for exchange-rate endpoints ({@code /api/exchange-rates}).
 */
@DisplayName("Exchange-rate endpoints integration tests (IT)")
class ExchangeRateEndpointsIntegrationTest extends IntegrationTestBase {

    /** Prefix for test currencies so cleanup doesn't affect shared data. */
    private static final String IT_CUR_PREFIX = "ITE";
    private static final String IT_USER_PREFIX = "iter_";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private CurrencyRepository currencyRepository;
    @Autowired private CurrencyExchangeRateRepository exchangeRateRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanIntegrationData() {
        // remove test exchange rates
        mongoTemplate.remove(
                Query.query(new Criteria().orOperator(
                        Criteria.where("sourceCurrency").regex("^" + IT_CUR_PREFIX),
                        Criteria.where("targetCurrency").regex("^" + IT_CUR_PREFIX))),
                CurrencyExchangeRateEntity.class);
        // remove test currencies
        mongoTemplate.remove(
                Query.query(Criteria.where("code").regex("^" + IT_CUR_PREFIX)),
                CurrencyEntity.class);
        // remove test users
        mongoTemplate.remove(
                Query.query(Criteria.where("username").regex("^" + IT_USER_PREFIX)),
                UserEntity.class);

        // seed two currencies for the tests
        seedCurrency(IT_CUR_PREFIX + "A", "IT ExRate A");
        seedCurrency(IT_CUR_PREFIX + "B", "IT ExRate B");
    }

    // ============================================================ GET /api/exchange-rates

    @Test
    void getAllExchangeRates_authenticated_returns200() throws Exception {
        seedRate(IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("2.500000"));
        String jwt = signUpUser("iter_list", "iter_list@test.com");

        mockMvc.perform(get("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$." + IT_CUR_PREFIX + "A_" + IT_CUR_PREFIX + "B").exists());
    }

    @Test
    void getAllExchangeRates_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================ GET /api/exchange-rates/rate

    @Test
    void getExchangeRate_found_returns200() throws Exception {
        seedRate(IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("3.000000"));
        String jwt = signUpUser("iter_rate", "iter_rate@test.com");

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", IT_CUR_PREFIX + "A")
                        .param("target", IT_CUR_PREFIX + "B")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency", is(IT_CUR_PREFIX + "A")))
                .andExpect(jsonPath("$.targetCurrency", is(IT_CUR_PREFIX + "B")))
                .andExpect(jsonPath("$.exchangeRate", closeTo(3.0, 0.01)));
    }

    @Test
    void getExchangeRate_notFound_returns400() throws Exception {
        String jwt = signUpUser("iter_nf", "iter_nf@test.com");

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", IT_CUR_PREFIX + "A")
                        .param("target", IT_CUR_PREFIX + "B")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getExchangeRate_sameCurrency_returns400() throws Exception {
        String jwt = signUpUser("iter_same", "iter_same@test.com");

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", IT_CUR_PREFIX + "A")
                        .param("target", IT_CUR_PREFIX + "A")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getExchangeRate_unsupportedCurrency_returns400() throws Exception {
        String jwt = signUpUser("iter_unsup", "iter_unsup@test.com");

        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", IT_CUR_PREFIX + "A")
                        .param("target", "ZZZZZZ")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ POST /api/exchange-rates (admin)

    @Test
    void setExchangeRate_asAdmin_returns201AndCreatesInverse() throws Exception {
        String adminJwt = signInAsAdmin();
        SetExchangeRateRequest req = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("4.5"));

        mockMvc.perform(post("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceCurrency", is(IT_CUR_PREFIX + "A")))
                .andExpect(jsonPath("$.targetCurrency", is(IT_CUR_PREFIX + "B")))
                .andExpect(jsonPath("$.exchangeRate", closeTo(4.5, 0.01)));

        // verify inverse was created
        mockMvc.perform(get("/api/exchange-rates/rate")
                        .param("source", IT_CUR_PREFIX + "B")
                        .param("target", IT_CUR_PREFIX + "A")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeRate", closeTo(0.222222, 0.001)));
    }

    @Test
    void setExchangeRate_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser("iter_noadm", "iter_noadm@test.com");
        SetExchangeRateRequest req = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("2.0"));

        mockMvc.perform(post("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void setExchangeRate_noAuth_returns401() throws Exception {
        SetExchangeRateRequest req = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("2.0"));

        mockMvc.perform(post("/api/exchange-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setExchangeRate_sameCurrency_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        SetExchangeRateRequest req = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "A", new BigDecimal("1.0"));

        mockMvc.perform(post("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ PUT /api/exchange-rates (admin)

    @Test
    void updateExchangeRate_asAdmin_returns200() throws Exception {
        String adminJwt = signInAsAdmin();
        // create first
        SetExchangeRateRequest create = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("2.0"));
        mockMvc.perform(post("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated());

        // update
        SetExchangeRateRequest update = new SetExchangeRateRequest(
                IT_CUR_PREFIX + "A", IT_CUR_PREFIX + "B", new BigDecimal("5.0"));
        mockMvc.perform(put("/api/exchange-rates")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeRate", closeTo(5.0, 0.01)));
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

    private void seedCurrency(String code, String name) {
        if (!currencyRepository.existsByCode(code)) {
            currencyRepository.save(new CurrencyEntity(null, name, code));
        }
    }

    private void seedRate(String source, String target, BigDecimal rate) {
        exchangeRateRepository.save(new CurrencyExchangeRateEntity(null, source, target, rate));
    }
}
