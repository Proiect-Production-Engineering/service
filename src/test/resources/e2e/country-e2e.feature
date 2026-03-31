Feature: Country management (end-to-end)
  As an admin
  I want to create, query, and delete countries
  So that bank accounts can use valid country codes

  @E2E
  Scenario: Admin CRUD lifecycle for countries
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I sign in as admin for country management
    When I create a country with a unique code
    Then the country creation should succeed with status 201

    When I query all countries
    Then the country list should include the new country

    When I query the country by its code
    Then the country response should match the created country

    When I delete the created country
    Then the country deletion should succeed with status 204

  @E2E
  Scenario: Regular user cannot create a country
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I register and sign in as a regular user for country tests
    When the regular user attempts to create a country
    Then the country creation should be forbidden with status 403
