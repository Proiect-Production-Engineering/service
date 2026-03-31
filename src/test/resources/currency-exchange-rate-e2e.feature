Feature: Currency exchange rate retrieval
  As an authenticated user
  I want to retrieve the exchange rate between two currencies
  So that I can see how much my money is worth

  @E2E
  Scenario: Retrieve EUR to RON exchange rate successfully
    Given the service base URL is "http://localhost:8080"
    And a new user is registered and authenticated
    When I request the exchange rate from "EUR" to "RON"
    Then the response status should be 200
    And the response should contain a positive exchange rate
