package ro.unibuc.prodeng.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * E2E steps for admin transaction search. Requires a running app; align {@code ADMIN_PASSWORD}
 * with the server (defaults to {@code test-admin-password} to match {@link ro.unibuc.prodeng.integration.IntegrationTestBase}).
 */
public class AdminSearchE2ESteps {

    private static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private final RestTemplate restTemplate = new RestTemplate();

    private String baseUrl;
    private String adminJwt;
    private ResponseEntity<String> lastSearchResponse;

    @Given("the admin E2E service base URL is {string}")
    public void theAdminE2EServiceBaseUrlIs(String url) {
        this.baseUrl = url;
    }

    @When("the admin signs in with password from environment or default")
    public void theAdminSignsInWithPasswordFromEnvironmentOrDefault() {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            password = DEFAULT_ADMIN_PASSWORD;
        }

        Map<String, String> signInBody = new HashMap<>();
        signInBody.put("username", "admin");
        signInBody.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(signInBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/auth/signin", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        this.adminJwt = response.getBody();
        assertNotNull(adminJwt);
        assertFalse(adminJwt.isBlank());
    }

    @Then("the admin JWT is present")
    public void theAdminJwtIsPresent() {
        assertNotNull(adminJwt);
    }

    @When("the admin searches transactions with an empty filter body")
    public void theAdminSearchesTransactionsWithAnEmptyFilterBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminJwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of(), headers);

        lastSearchResponse = restTemplate.exchange(
                baseUrl + "/api/admin/transactions/search",
                HttpMethod.POST,
                entity,
                String.class);
    }

    @Then("the admin search response status is {int}")
    public void theAdminSearchResponseStatusIs(int expected) {
        assertNotNull(lastSearchResponse);
        assertEquals(HttpStatus.valueOf(expected), lastSearchResponse.getStatusCode());
    }
}
