Feature: Full banking flow (end-to-end)
  As a SafeTransfer user
  I want to register, create accounts, and view my balance sheet
  So that I can verify the entire system works end to end

  @E2E
  Scenario: Complete user-to-balance-sheet flow
    Given the SafeTransfer API is running at "http://localhost:8080"
    And I sign in as admin for the full flow
    And I ensure currencies "EUR" and "RON" exist in the system
    And I ensure an exchange rate exists from "EUR" to "RON" at rate 4.95

    When a new sender user registers and creates a "EUR" account in "RO"
    And a new receiver user registers and creates a "RON" account in "RO"

    Then the sender balance sheet should display currency "EUR"
    And the receiver balance sheet should display currency "RON"
