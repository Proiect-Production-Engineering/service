@E2E
Feature: Admin transaction search
  As an administrator
  I want to search transactions globally
  So that I can monitor activity across accounts

  Scenario: Admin signs in and searches transactions
    Given the admin E2E service base URL is "http://localhost:8080"
    When the admin signs in with password from environment or default
    Then the admin JWT is present
    When the admin searches transactions with an empty filter body
    Then the admin search response status is 200
