package ro.unibuc.prodeng.e2e;

import static org.junit.Assert.*;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import ro.unibuc.prodeng.request.*;

/**
 * Step definitions for all new E2E features (user, bank-account, country,
 * currency, transfer-flow).
 * <p>
 * Uses step text that does NOT conflict with the existing
 * {@link CurrencyExchangeRateE2ESteps} (which owns "the service base URL is …",
 * "a new user is registered and authenticated", and "the response status should
 * be …").
 */
public class SafeTransferE2ESteps {

    private final RestTemplate rest = new RestTemplate();

    // ============ shared scenario state ============
    private String baseUrl;
    private String adminJwt;

    // user scenario
    private String regUsername;
    private String regEmail;
    private String regPassword;
    private String userJwt;
    private int signUpStatus;
    private int signInStatus;
    private String signInJwt;
    private int profileStatus;
    private Map<?, ?> profileBody;

    // bank-account scenario
    private String bankUserJwt;
    private String accountId;
    private String iban;
    private int createAcctStatus;
    private Map<?, ?> createAcctBody;
    private int listAcctStatus;
    private List<?> listAcctBody;
    private int balanceSheetStatus;
    private Map<?, ?> balanceSheetBody;
    private int dupAcctStatus;

    // country scenario
    private String countryId;
    private String countryCode;
    private String countryName;
    private int createCountryStatus;
    private Map<?, ?> createCountryBody;
    private List<?> allCountriesBody;
    private Map<?, ?> countryByCodeBody;
    private int deleteCountryStatus;
    private String regUserJwtForCountry;

    // currency scenario
    private String currencyId;
    private String currencyCode;
    private String currencyName;
    private int createCurrencyStatus;
    private Map<?, ?> createCurrencyBody;
    private List<?> allCurrenciesBody;
    private Map<?, ?> currencyByCodeBody;
    private int deleteCurrencyStatus;
    private int dupCurrencyStatus;
    private String regUserJwtForCurrency;

    // transfer-flow scenario
    private String senderJwt;
    private String senderAccountId;
    private String receiverJwt;
    private String receiverAccountId;

    // ================================================================
    //  SHARED: base URL + admin sign-in
    // ================================================================

    @Given("the SafeTransfer API is running at {string}")
    public void theSafeTransferApiIsRunningAt(String url) {
        this.baseUrl = url;
    }

    private String adminSignIn() {
        if (adminJwt != null) return adminJwt;
        String pw = System.getenv("ADMIN_PASSWORD");
        if (pw == null || pw.isBlank()) pw = "admin";
        SignInRequest req = new SignInRequest("admin", pw);
        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/api/auth/signin", req, String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        adminJwt = resp.getBody();
        return adminJwt;
    }

    // ================================================================
    //  USER ACCOUNT STEPS
    // ================================================================

    @When("I register a new user with unique credentials")
    public void iRegisterANewUser() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        regUsername = "e2eu_" + sfx;
        regEmail = regUsername + "@e2e.test";
        regPassword = "E2ePass123!";

        SignUpRequest req = new SignUpRequest(regUsername, regEmail, regPassword);
        try {
            ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", req, String.class);
            signUpStatus = r.getStatusCode().value();
            userJwt = r.getBody();
        } catch (HttpClientErrorException e) { signUpStatus = e.getStatusCode().value(); }
    }

    @Then("the registration should succeed with a JWT")
    public void registrationShouldSucceed() {
        assertEquals(200, signUpStatus);
        assertNotNull(userJwt);
        assertTrue(userJwt.startsWith("eyJ"));
    }

    @When("I sign in with the registered credentials")
    public void iSignInRegistered() {
        SignInRequest req = new SignInRequest(regUsername, regPassword);
        try {
            ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signin", req, String.class);
            signInStatus = r.getStatusCode().value();
            signInJwt = r.getBody();
            userJwt = signInJwt;
        } catch (HttpClientErrorException e) { signInStatus = e.getStatusCode().value(); }
    }

    @Then("the sign-in should succeed with a JWT")
    public void signInShouldSucceed() {
        assertEquals(200, signInStatus);
        assertNotNull(signInJwt);
        assertTrue(signInJwt.startsWith("eyJ"));
    }

    @When("I sign in with an incorrect password")
    public void iSignInIncorrectPassword() {
        SignInRequest req = new SignInRequest(regUsername, "WrongPassword999!");
        try {
            ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signin", req, String.class);
            signInStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { signInStatus = e.getStatusCode().value(); }
    }

    @Then("the sign-in should fail with status {int}")
    public void signInFail(int expected) { assertEquals(expected, signInStatus); }

    @When("I fetch my user profile")
    public void iFetchProfile() {
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/users/me",
                    HttpMethod.GET, new HttpEntity<>(auth(userJwt)), Map.class);
            profileStatus = r.getStatusCode().value();
            profileBody = r.getBody();
        } catch (HttpClientErrorException e) { profileStatus = e.getStatusCode().value(); }
    }

    @Then("the profile should match my registration details")
    public void profileShouldMatch() {
        assertEquals(200, profileStatus);
        assertNotNull(profileBody);
        assertEquals(regUsername, profileBody.get("username"));
        assertEquals(regEmail, profileBody.get("email"));
    }

    // ================================================================
    //  BANK ACCOUNT STEPS
    // ================================================================

    @Given("I register and sign in as a new bank user")
    public void registerBankUser() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        SignUpRequest req = new SignUpRequest("e2eba_" + sfx, "e2eba_" + sfx + "@e2e.test", "E2ePass123!");
        ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", req, String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        bankUserJwt = r.getBody();
    }

    @When("I create a bank account with currency {string} in country {string}")
    public void iCreateBankAccount(String cur, String country) {
        doCreateBankAccount(cur, country, false);
    }

    @When("I create a duplicate bank account with currency {string} in country {string}")
    public void iCreateDuplicateBankAccount(String cur, String country) {
        doCreateBankAccount(cur, country, true);
    }

    private void doCreateBankAccount(String cur, String country, boolean expectFail) {
        CreateBankAccountRequest req = new CreateBankAccountRequest(cur, country, "E2E Holder");
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/accounts",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(bankUserJwt)), Map.class);
            int status = r.getStatusCode().value();
            if (expectFail) { dupAcctStatus = status; return; }
            createAcctStatus = status;
            createAcctBody = r.getBody();
            if (createAcctBody != null) {
                accountId = (String) createAcctBody.get("id");
                iban = (String) createAcctBody.get("iban");
            }
        } catch (HttpClientErrorException e) {
            if (expectFail) dupAcctStatus = e.getStatusCode().value();
            else createAcctStatus = e.getStatusCode().value();
        }
    }

    @Then("the account creation should succeed with status {int}")
    public void acctCreationSucceed(int s) { assertEquals(s, createAcctStatus); }

    @And("the account response should include an ID and IBAN")
    public void acctResponseIdIban() {
        assertNotNull(accountId);
        assertNotNull(iban);
        assertFalse(accountId.isEmpty());
    }

    @Then("the duplicate account creation should fail with status {int}")
    public void dupAcctFail(int s) { assertEquals(s, dupAcctStatus); }

    @When("I list my bank accounts")
    public void iListMyBankAccounts() {
        ResponseEntity<List> r = rest.exchange(baseUrl + "/api/accounts/me",
                HttpMethod.GET, new HttpEntity<>(auth(bankUserJwt)), List.class);
        listAcctStatus = r.getStatusCode().value();
        listAcctBody = r.getBody();
    }

    @Then("the account list should contain {int} account")
    public void acctListSize(int n) {
        assertEquals(200, listAcctStatus);
        assertNotNull(listAcctBody);
        assertEquals(n, listAcctBody.size());
    }

    @When("I view the balance sheet for my new account")
    public void iViewBalanceSheet() {
        ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/accounts/" + accountId + "/balance-sheet",
                HttpMethod.GET, new HttpEntity<>(auth(bankUserJwt)), Map.class);
        balanceSheetStatus = r.getStatusCode().value();
        balanceSheetBody = r.getBody();
    }

    @Then("the balance sheet should show currency {string}")
    public void bsCurrency(String cur) {
        assertEquals(200, balanceSheetStatus);
        assertNotNull(balanceSheetBody);
        assertEquals(cur, balanceSheetBody.get("currency"));
    }

    // ================================================================
    //  COUNTRY STEPS
    // ================================================================

    @Given("I sign in as admin for country management")
    public void adminForCountry() { adminSignIn(); }

    @Given("I register and sign in as a regular user for country tests")
    public void regUserForCountry() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        SignUpRequest req = new SignUpRequest("e2ecty_" + sfx, "e2ecty_" + sfx + "@e2e.test", "E2ePass123!");
        ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", req, String.class);
        regUserJwtForCountry = r.getBody();
    }

    @When("I create a country with a unique code")
    public void iCreateCountry() {
        String sfx = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        countryCode = "E" + sfx;
        countryName = "E2E Country " + sfx;
        String pattern = countryCode + "XXXXXXXXXXXXXXXX";

        CreateCountryRequest req = new CreateCountryRequest(countryName, countryCode, pattern);
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/countries",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(adminJwt)), Map.class);
            createCountryStatus = r.getStatusCode().value();
            createCountryBody = r.getBody();
            if (createCountryBody != null) countryId = (String) createCountryBody.get("id");
        } catch (HttpClientErrorException e) { createCountryStatus = e.getStatusCode().value(); }
    }

    @When("the regular user attempts to create a country")
    public void regUserCreatesCountry() {
        String sfx = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        CreateCountryRequest req = new CreateCountryRequest("Blk", "B" + sfx, "B" + sfx + "XXXXXXXXXXXXXXXX");
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/countries",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(regUserJwtForCountry)), Map.class);
            createCountryStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { createCountryStatus = e.getStatusCode().value(); }
    }

    @Then("the country creation should succeed with status {int}")
    public void countryCreateSucceed(int s) { assertEquals(s, createCountryStatus); }

    @Then("the country creation should be forbidden with status {int}")
    public void countryCreateForbidden(int s) { assertEquals(s, createCountryStatus); }

    @When("I query all countries")
    public void queryAllCountries() {
        ResponseEntity<List> r = rest.exchange(baseUrl + "/api/countries",
                HttpMethod.GET, new HttpEntity<>(auth(adminJwt)), List.class);
        allCountriesBody = r.getBody();
    }

    @SuppressWarnings("unchecked")
    @Then("the country list should include the new country")
    public void countryListIncludes() {
        assertNotNull(allCountriesBody);
        boolean found = allCountriesBody.stream()
                .map(o -> (Map<String, Object>) o)
                .anyMatch(m -> countryCode.equals(m.get("code")));
        assertTrue("Country " + countryCode + " not found", found);
    }

    @When("I query the country by its code")
    public void queryCountryByCode() {
        ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/countries/by-code?code=" + countryCode,
                HttpMethod.GET, new HttpEntity<>(auth(adminJwt)), Map.class);
        countryByCodeBody = r.getBody();
    }

    @Then("the country response should match the created country")
    public void countryByCodeMatch() {
        assertNotNull(countryByCodeBody);
        assertEquals(countryCode, countryByCodeBody.get("code"));
        assertEquals(countryName, countryByCodeBody.get("name"));
    }

    @When("I delete the created country")
    public void deleteCountry() {
        try {
            ResponseEntity<Void> r = rest.exchange(baseUrl + "/api/countries/" + countryId,
                    HttpMethod.DELETE, new HttpEntity<>(auth(adminJwt)), Void.class);
            deleteCountryStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { deleteCountryStatus = e.getStatusCode().value(); }
    }

    @Then("the country deletion should succeed with status {int}")
    public void countryDeleteSucceed(int s) { assertEquals(s, deleteCountryStatus); }

    // ================================================================
    //  CURRENCY STEPS
    // ================================================================

    @Given("I sign in as admin for currency management")
    public void adminForCurrency() { adminSignIn(); }

    @Given("I register and sign in as a regular user for currency tests")
    public void regUserForCurrency() {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        SignUpRequest req = new SignUpRequest("e2ecur_" + sfx, "e2ecur_" + sfx + "@e2e.test", "E2ePass123!");
        ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", req, String.class);
        regUserJwtForCurrency = r.getBody();
    }

    @When("I create a currency with a unique code")
    public void iCreateCurrency() {
        String sfx = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        currencyCode = "E" + sfx;
        currencyName = "E2E Currency " + sfx;

        CreateCurrencyRequest req = new CreateCurrencyRequest(currencyName, currencyCode);
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/currencies",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(adminJwt)), Map.class);
            createCurrencyStatus = r.getStatusCode().value();
            createCurrencyBody = r.getBody();
            if (createCurrencyBody != null) currencyId = (String) createCurrencyBody.get("id");
        } catch (HttpClientErrorException e) { createCurrencyStatus = e.getStatusCode().value(); }
    }

    @When("I create another currency with the same code")
    public void iCreateDupCurrency() {
        CreateCurrencyRequest req = new CreateCurrencyRequest("Dup", currencyCode);
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/currencies",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(adminJwt)), Map.class);
            dupCurrencyStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { dupCurrencyStatus = e.getStatusCode().value(); }
    }

    @When("the regular user attempts to create a currency")
    public void regUserCreatesCurrency() {
        String sfx = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        CreateCurrencyRequest req = new CreateCurrencyRequest("Blk", "B" + sfx);
        try {
            ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/currencies",
                    HttpMethod.POST, new HttpEntity<>(req, authJson(regUserJwtForCurrency)), Map.class);
            createCurrencyStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { createCurrencyStatus = e.getStatusCode().value(); }
    }

    @Then("the currency creation should succeed with status {int}")
    public void currencyCreateSucceed(int s) { assertEquals(s, createCurrencyStatus); }

    @Then("the duplicate currency creation should fail with status {int}")
    public void dupCurrencyFail(int s) { assertEquals(s, dupCurrencyStatus); }

    @Then("the currency creation should be forbidden with status {int}")
    public void currencyCreateForbidden(int s) { assertEquals(s, createCurrencyStatus); }

    @When("I query all currencies")
    public void queryAllCurrencies() {
        ResponseEntity<List> r = rest.exchange(baseUrl + "/api/currencies",
                HttpMethod.GET, new HttpEntity<>(auth(adminJwt)), List.class);
        allCurrenciesBody = r.getBody();
    }

    @SuppressWarnings("unchecked")
    @Then("the currency list should include the new currency")
    public void currencyListIncludes() {
        assertNotNull(allCurrenciesBody);
        boolean found = allCurrenciesBody.stream()
                .map(o -> (Map<String, Object>) o)
                .anyMatch(m -> currencyCode.equals(m.get("code")));
        assertTrue("Currency " + currencyCode + " not found", found);
    }

    @When("I query the currency by its code")
    public void queryCurrencyByCode() {
        ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/currencies/by-code?code=" + currencyCode,
                HttpMethod.GET, new HttpEntity<>(auth(adminJwt)), Map.class);
        currencyByCodeBody = r.getBody();
    }

    @Then("the currency response should match the created currency")
    public void currencyByCodeMatch() {
        assertNotNull(currencyByCodeBody);
        assertEquals(currencyCode, currencyByCodeBody.get("code"));
        assertEquals(currencyName, currencyByCodeBody.get("name"));
    }

    @When("I delete the created currency")
    public void deleteCurrency() {
        try {
            ResponseEntity<Void> r = rest.exchange(baseUrl + "/api/currencies/" + currencyId,
                    HttpMethod.DELETE, new HttpEntity<>(auth(adminJwt)), Void.class);
            deleteCurrencyStatus = r.getStatusCode().value();
        } catch (HttpClientErrorException e) { deleteCurrencyStatus = e.getStatusCode().value(); }
    }

    @Then("the currency deletion should succeed with status {int}")
    public void currencyDeleteSucceed(int s) { assertEquals(s, deleteCurrencyStatus); }

    // ================================================================
    //  TRANSFER FLOW STEPS
    // ================================================================

    @Given("I sign in as admin for the full flow")
    public void adminForFullFlow() { adminSignIn(); }

    @Given("I ensure currencies {string} and {string} exist in the system")
    public void ensureCurrencies(String c1, String c2) {
        ensureCurrencyExists(c1);
        ensureCurrencyExists(c2);
    }

    private void ensureCurrencyExists(String code) {
        try {
            rest.exchange(baseUrl + "/api/currencies/by-code?code=" + code,
                    HttpMethod.GET, new HttpEntity<>(auth(adminJwt)), Map.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                CreateCurrencyRequest req = new CreateCurrencyRequest(code + " Dollar", code);
                rest.exchange(baseUrl + "/api/currencies", HttpMethod.POST,
                        new HttpEntity<>(req, authJson(adminJwt)), Map.class);
            }
        }
    }

    @Given("I ensure an exchange rate exists from {string} to {string} at rate {double}")
    public void ensureExchangeRate(String src, String tgt, double rate) {
        SetExchangeRateRequest req = new SetExchangeRateRequest(src, tgt, BigDecimal.valueOf(rate));
        try {
            rest.exchange(baseUrl + "/api/exchange-rates", HttpMethod.POST,
                    new HttpEntity<>(req, authJson(adminJwt)), Map.class);
        } catch (HttpClientErrorException e) {
            // already exists — update
            rest.exchange(baseUrl + "/api/exchange-rates", HttpMethod.PUT,
                    new HttpEntity<>(req, authJson(adminJwt)), Map.class);
        }
    }

    @When("a new sender user registers and creates a {string} account in {string}")
    public void senderRegisters(String cur, String country) {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        SignUpRequest su = new SignUpRequest("e2esnd_" + sfx, "e2esnd_" + sfx + "@e2e.test", "E2ePass123!");
        ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", su, String.class);
        senderJwt = r.getBody();

        CreateBankAccountRequest ca = new CreateBankAccountRequest(cur, country, "Sender");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.exchange(baseUrl + "/api/accounts",
                HttpMethod.POST, new HttpEntity<>(ca, authJson(senderJwt)), Map.class).getBody();
        senderAccountId = (String) body.get("id");
    }

    @And("a new receiver user registers and creates a {string} account in {string}")
    public void receiverRegisters(String cur, String country) {
        String sfx = UUID.randomUUID().toString().substring(0, 8);
        SignUpRequest su = new SignUpRequest("e2ercv_" + sfx, "e2ercv_" + sfx + "@e2e.test", "E2ePass123!");
        ResponseEntity<String> r = rest.postForEntity(baseUrl + "/api/auth/signup", su, String.class);
        receiverJwt = r.getBody();

        CreateBankAccountRequest ca = new CreateBankAccountRequest(cur, country, "Receiver");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.exchange(baseUrl + "/api/accounts",
                HttpMethod.POST, new HttpEntity<>(ca, authJson(receiverJwt)), Map.class).getBody();
        receiverAccountId = (String) body.get("id");
    }

    @Then("the sender balance sheet should display currency {string}")
    public void senderBsCurrency(String cur) {
        Map<?, ?> bs = getBalanceSheet(senderJwt, senderAccountId);
        assertEquals(cur, bs.get("currency"));
    }

    @And("the receiver balance sheet should display currency {string}")
    public void receiverBsCurrency(String cur) {
        Map<?, ?> bs = getBalanceSheet(receiverJwt, receiverAccountId);
        assertEquals(cur, bs.get("currency"));
    }

    private Map<?, ?> getBalanceSheet(String jwt, String acctId) {
        ResponseEntity<Map> r = rest.exchange(baseUrl + "/api/accounts/" + acctId + "/balance-sheet",
                HttpMethod.GET, new HttpEntity<>(auth(jwt)), Map.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        return r.getBody();
    }

    // ================================================================
    //  HELPERS
    // ================================================================

    private HttpHeaders auth(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + jwt);
        return h;
    }

    private HttpHeaders authJson(String jwt) {
        HttpHeaders h = auth(jwt);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
