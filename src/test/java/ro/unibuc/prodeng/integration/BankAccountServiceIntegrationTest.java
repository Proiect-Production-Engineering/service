package ro.unibuc.prodeng.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import ro.unibuc.prodeng.IntegrationTestBase;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.TransactionResponse;
import ro.unibuc.prodeng.service.BankAccountService;

class BankAccountServiceIntegrationTest extends IntegrationTestBase {

    private static final String CURRENT_USER_ID = "it-user-1";

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        // Arrange
        transactionRepository.deleteAll();
        bankAccountRepository.deleteAll();

        UserDetails principal = UserDetails.builder()
                .id(CURRENT_USER_ID)
                .username("it-user")
                .email("it-user@example.com")
                .password("password")
                .authorities(Collections.emptyList())
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void transfer_validRequest_updatesBalancesAndPersistsTransactions() {
        // Arrange
        BankAccountEntity sourceAccount = BankAccountEntity.builder()
                .iban("IT-IBAN-SOURCE")
                .userId(CURRENT_USER_ID)
                .currencyCode("EUR")
                .countryCode("RO")
                .accountHolderName("Source User")
                .balance(new BigDecimal("1000.00"))
                .deleted(false)
                .build();

        BankAccountEntity targetAccount = BankAccountEntity.builder()
                .iban("IT-IBAN-TARGET")
                .userId("other-user")
                .currencyCode("EUR")
                .countryCode("RO")
                .accountHolderName("Target User")
                .balance(new BigDecimal("100.00"))
                .deleted(false)
                .build();

        sourceAccount = bankAccountRepository.save(sourceAccount);
        targetAccount = bankAccountRepository.save(targetAccount);

        CreateTransferRequest request = new CreateTransferRequest(
                sourceAccount.getId(),
                targetAccount.getId(),
                new BigDecimal("250.00"),
                "IT transfer"
        );

        // Act
        List<TransactionResponse> responses = bankAccountService.transfer(request);

        // Assert
        BankAccountEntity updatedSource = bankAccountRepository.findById(sourceAccount.getId()).orElseThrow();
        BankAccountEntity updatedTarget = bankAccountRepository.findById(targetAccount.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("750.00").compareTo(updatedSource.getBalance()));
        assertEquals(0, new BigDecimal("350.00").compareTo(updatedTarget.getBalance()));

        assertEquals(2, responses.size());

        List<TransactionEntity> sourceTransactions = transactionRepository
                .findByAccountIdOrderByTimestampAsc(sourceAccount.getId());
        List<TransactionEntity> targetTransactions = transactionRepository
                .findByAccountIdOrderByTimestampAsc(targetAccount.getId());

        assertEquals(1, sourceTransactions.size());
        assertEquals(1, targetTransactions.size());

        TransactionEntity debit = sourceTransactions.get(0);
        TransactionEntity credit = targetTransactions.get(0);

        assertEquals(TransactionType.DEBIT, debit.type());
        assertEquals(TransactionType.CREDIT, credit.type());
        assertEquals(0, new BigDecimal("250.00").compareTo(debit.amount()));
        assertEquals(0, new BigDecimal("250.00").compareTo(credit.amount()));
        assertEquals("IT transfer", debit.description());
        assertEquals("IT transfer", credit.description());
        assertNotNull(debit.timestamp());
        assertNotNull(credit.timestamp());
    }
}
