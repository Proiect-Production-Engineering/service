Feature: Currency management (end-to-end)
  As an admin
  I want to create, query, and delete currencies
  So that accounts and exchange rates can use valid currency codes

  @E2E
  Scenario: Admin CRUD lifecycle for currencies
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I sign in as admin for currency management
    When I create a currency with a unique code
    Then the currency creation should succeed with status 201

    When I query all currencies
    Then the currency list should include the new currency

    When I query the currency by its code
    Then the currency response should match the created currency

    When I delete the created currency
    Then the currency deletion should succeed with status 204

  @E2E
  Scenario: Duplicate currency code is rejected
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I sign in as admin for currency management
    And I create a currency with a unique code
    When I create another currency with the same code
    Then the duplicate currency creation should fail with status 400

  @E2E
  Scenario: Regular user cannot create a currency
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I register and sign in as a regular user for currency tests
    When the regular user attempts to create a currency
    Then the currency creation should be forbidden with status 403
