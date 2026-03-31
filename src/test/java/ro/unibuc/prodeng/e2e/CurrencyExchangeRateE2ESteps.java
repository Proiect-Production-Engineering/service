package ro.unibuc.prodeng.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.response.ExchangeRateResponse;

public class CurrencyExchangeRateE2ESteps {

    private final RestTemplate restTemplate = new RestTemplate();

    private String baseUrl;
    private String jwtToken;
    private ResponseEntity<ExchangeRateResponse> lastResponse;

    @Given("the service base URL is {string}")
    public void theServiceBaseUrlIs(String url) {
        this.baseUrl = url;
    }

    @Given("a new user is registered and authenticated")
    public void aNewUserIsRegisteredAndAuthenticated() {
        String uniqueUsername = "e2e" + UUID.randomUUID().toString().substring(0, 8);
        String email = uniqueUsername + "@example.com";
        String password = "Password123!";

        SignUpRequest request = new SignUpRequest(uniqueUsername, email, password);

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/api/auth/signup", request, String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        this.jwtToken = response.getBody();
        assertNotNull("JWT token should not be null", jwtToken);
    }

    @When("I request the exchange rate from {string} to {string}")
    public void iRequestTheExchangeRateFromTo(String source, String target) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = String.format("%s/api/exchange-rates/rate?source=%s&target=%s", baseUrl, source, target);

        lastResponse = restTemplate.exchange(url, HttpMethod.GET, entity, ExchangeRateResponse.class);
    }

    @Then("the response status should be {int}")
    public void theResponseStatusShouldBe(Integer expectedStatus) {
        assertNotNull("Response should not be null", lastResponse);
        assertEquals(HttpStatus.valueOf(expectedStatus), lastResponse.getStatusCode());
    }

    @Then("the response should contain a positive exchange rate")
    public void theResponseShouldContainAPositiveExchangeRate() {
        assertNotNull("Response should not be null", lastResponse);

        ExchangeRateResponse body = lastResponse.getBody();
        assertNotNull("Response body should not be null", body);
        assertNotNull("Exchange rate should not be null", body.exchangeRate());
        assertTrue("Exchange rate should be greater than zero",
            body.exchangeRate().compareTo(BigDecimal.ZERO) > 0
        );
    }
}
