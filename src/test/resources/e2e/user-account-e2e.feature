Feature: User account lifecycle (end-to-end)
  As a user of SafeTransfer
  I want to sign up, sign in, and view my profile
  So that I can access the banking platform

  @E2E
  Scenario: Register, sign in, and view profile
    Given the SafeTransfer API is running at "http://localhost:8080"
    When I register a new user with unique credentials
    Then the registration should succeed with a JWT

    When I sign in with the registered credentials
    Then the sign-in should succeed with a JWT

    When I fetch my user profile
    Then the profile should match my registration details

  @E2E
  Scenario: Sign in with incorrect password fails
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I register a new user with unique credentials
    When I sign in with an incorrect password
    Then the sign-in should fail with status 401
