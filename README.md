# SafeTransfer

## Team
- **Team Name:** SafeTransfer Squad
- **Members:**
  - Aelenei Alex-Ioan - software developer
  - Dima Florin-Alexandru - software developer
  - Boca Bogdan - software developer

## Project Description

SafeTransfer is a secure banking application that allows users to manage bank accounts and perform financial transactions. The platform uses JWT-based authentication and automatically provisions a bank account with a valid IBAN upon user registration, supporting multiple currencies. Users can create additional accounts in specific currencies, view their account balances, and initiate transactions between accounts. Administrators have elevated privileges, including the ability to view and delete any user's accounts and search across all transactions in the system.

### Key Features
- JWT-based authentication
- User accounts
- Transactions between accounts
- Account balance sheets
- Admininistrator visibility over all transactions

### Technical Stack
- **Backend:** Spring Boot (Java 21)
- **Database:** MongoDB
- **API:** RESTful
- **Testing:** JUnit, Mockito, Cucumber
- **Monitoring:** Prometheus, Grafana
- **Deployment:** Docker

## Contributing

All team members follow trunk-based development:
1. Create feature branch from `main`
2. Make changes and commit with clear messages
3. Create PR and request review
4. Address feedback
5. Merge after approval