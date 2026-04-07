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

import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.CountryRepository;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateCountryRequest;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for country endpoints ({@code /api/countries}).
 */
@DisplayName("Country endpoints integration tests (IT)")
class CountryEndpointsIntegrationTest extends IntegrationTestBase {

    private static final String IT_COUNTRY_PREFIX = "X";
    private static final String IT_USER_PREFIX = "itcty_";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private CountryRepository countryRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanIntegrationData() {
        mongoTemplate.remove(
                Query.query(Criteria.where("code").regex("^" + IT_COUNTRY_PREFIX)),
                CountryEntity.class);
        mongoTemplate.remove(
                Query.query(Criteria.where("username").regex("^" + IT_USER_PREFIX)),
                UserEntity.class);
    }

    // ============================================================ GET /api/countries

    @Test
    void getAllCountries_authenticated_returns200() throws Exception {
        seedCountry(IT_COUNTRY_PREFIX + "A", "IT Country A", "XAXXXXXXXXXXXXXXXXX");
        String jwt = signUpUser("itcty_list", "itcty_list@test.com");

        mockMvc.perform(get("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.code=='" + IT_COUNTRY_PREFIX + "A')]").exists());
    }

    @Test
    void getAllCountries_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/countries"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================ GET /api/countries/{id}

    @Test
    void getCountryById_found_returns200() throws Exception {
        String id = seedCountry(IT_COUNTRY_PREFIX + "B", "IT Country B", "XBXXXXXXXXXXXXXXXXX");
        String jwt = signUpUser("itcty_byid", "itcty_byid@test.com");

        mockMvc.perform(get("/api/countries/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(IT_COUNTRY_PREFIX + "B")))
                .andExpect(jsonPath("$.name", is("IT Country B")));
    }

    @Test
    void getCountryById_notFound_returns404() throws Exception {
        String jwt = signUpUser("itcty_nf", "itcty_nf@test.com");

        mockMvc.perform(get("/api/countries/{id}", "nonexistent-id-123")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isNotFound());
    }

    // ============================================================ GET /api/countries/by-code

    @Test
    void getCountryByCode_found_returns200() throws Exception {
        seedCountry(IT_COUNTRY_PREFIX + "C", "IT Country C", "XCXXXXXXXXXXXXXXXXX");
        String jwt = signUpUser("itcty_code", "itcty_code@test.com");

        mockMvc.perform(get("/api/countries/by-code")
                        .param("code", IT_COUNTRY_PREFIX + "C")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(IT_COUNTRY_PREFIX + "C")));
    }

    @Test
    void getCountryByCode_notFound_returns404() throws Exception {
        String jwt = signUpUser("itcty_codenf", "itcty_codenf@test.com");

        mockMvc.perform(get("/api/countries/by-code")
                        .param("code", "ZZZZZZ")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt))
                .andExpect(status().isNotFound());
    }

    // ============================================================ POST /api/countries (admin)

    @Test
    void createCountry_asAdmin_returns201() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateCountryRequest req = new CreateCountryRequest(
                "IT Country D", IT_COUNTRY_PREFIX + "D", "XDXXXXXXXXXXXXXXXXX");

        mockMvc.perform(post("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is(IT_COUNTRY_PREFIX + "D")))
                .andExpect(jsonPath("$.name", is("IT Country D")))
                .andExpect(jsonPath("$.ibanPattern", is("XDXXXXXXXXXXXXXXXXX")))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createCountry_duplicateCode_returns400() throws Exception {
        seedCountry(IT_COUNTRY_PREFIX + "E", "Original", "XEXXXXXXXXXXXXXXXXX");
        String adminJwt = signInAsAdmin();
        CreateCountryRequest req = new CreateCountryRequest(
                "Duplicate", IT_COUNTRY_PREFIX + "E", "XEXXXXXXXXXXXXXXXXX");

        mockMvc.perform(post("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCountry_asRegularUser_returns403() throws Exception {
        String jwt = signUpUser("itcty_noadm", "itcty_noadm@test.com");
        CreateCountryRequest req = new CreateCountryRequest(
                "Blocked", IT_COUNTRY_PREFIX + "F", "XFXXXXXXXXXXXXXXXXX");

        mockMvc.perform(post("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCountry_noAuth_returns401() throws Exception {
        CreateCountryRequest req = new CreateCountryRequest(
                "NoAuth", IT_COUNTRY_PREFIX + "G", "XGXXXXXXXXXXXXXXXXX");

        mockMvc.perform(post("/api/countries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCountry_blankCode_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateCountryRequest req = new CreateCountryRequest("Bad", "", "XXXXXXXXXXXXXXXXXX1");

        mockMvc.perform(post("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCountry_ibanPatternTooShort_returns400() throws Exception {
        String adminJwt = signInAsAdmin();
        CreateCountryRequest req = new CreateCountryRequest("Short", IT_COUNTRY_PREFIX + "S", "TOOSHORT");

        mockMvc.perform(post("/api/countries")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ DELETE /api/countries/{id} (admin)

    @Test
    void deleteCountry_asAdmin_returns204() throws Exception {
        String id = seedCountry(IT_COUNTRY_PREFIX + "H", "To Delete", "XHXXXXXXXXXXXXXXXXX");
        String adminJwt = signInAsAdmin();

        mockMvc.perform(delete("/api/countries/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNoContent());

        // verify gone
        mockMvc.perform(get("/api/countries/{id}", id)
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCountry_notFound_returns404() throws Exception {
        String adminJwt = signInAsAdmin();

        mockMvc.perform(delete("/api/countries/{id}", "nonexistent-id-999")
                        .header(AuthenticationTokenFilter.HEADER_TITLE,
                                AuthenticationTokenFilter.HEADER_PREFIX + adminJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCountry_asRegularUser_returns403() throws Exception {
        String id = seedCountry(IT_COUNTRY_PREFIX + "I", "Protected", "XIXXXXXXXXXXXXXXXXX");
        String jwt = signUpUser("itcty_delnoadm", "itcty_delnoadm@test.com");

        mockMvc.perform(delete("/api/countries/{id}", id)
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

    /** Seeds a country directly in the DB and returns its ID. */
    private String seedCountry(String code, String name, String ibanPattern) {
        CountryEntity entity = new CountryEntity(null, name, code, ibanPattern);
        return countryRepository.save(entity).id();
    }
}
