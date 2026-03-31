Feature: Bank account lifecycle (end-to-end)
  As an authenticated user
  I want to create accounts, view them, and check my balance sheet
  So that I can manage my banking

  @E2E
  Scenario: Create account, list accounts, and view balance sheet
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I register and sign in as a new bank user
    When I create a bank account with currency "EUR" in country "RO"
    Then the account creation should succeed with status 201
    And the account response should include an ID and IBAN

    When I list my bank accounts
    Then the account list should contain 1 account

    When I view the balance sheet for my new account
    Then the balance sheet should show currency "EUR"

  @E2E
  Scenario: Duplicate currency account is rejected
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I register and sign in as a new bank user
    And I create a bank account with currency "EUR" in country "RO"
    When I create a duplicate bank account with currency "EUR" in country "RO"
    Then the duplicate account creation should fail with status 400
