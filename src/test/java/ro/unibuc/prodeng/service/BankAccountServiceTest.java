package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.TransactionResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BankAccountService bankAccountService;

    private static final String CURRENT_USER_ID = "user-1";

    @BeforeEach
    void setUpSecurityContext() {
        UserDetails principal = UserDetails.builder()
                .id(CURRENT_USER_ID)
                .username("john")
                .email("john@example.com")
                .password("secret")
                .authorities(List.of())
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

        private BankAccountEntity makeAccount(String id, String userId, String currency, boolean deleted, double balance) {
        BankAccountEntity account = new BankAccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setCurrencyCode(currency);
        account.setDeleted(deleted);
        account.setBalance(balance);
        return account;
    }

    private TransactionEntity makeTransaction(String id, String accountId, TransactionType type, BigDecimal amount) {
        return new TransactionEntity(id, accountId, type, amount, "desc", Instant.now());
    }
    
    @Test
    void testTransfer_validRequest_createsDebitAndCreditTransactions() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 1000.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        when(transactionRepository.saveAll(any(List.class))).thenAnswer(invocation -> {
            List<TransactionEntity> txs = invocation.getArgument(0);
            TransactionEntity debit = txs.get(0);
            TransactionEntity credit = txs.get(1);
            return List.of(
                    new TransactionEntity("tx-1", debit.accountId(), debit.type(), debit.amount(), debit.description(), debit.timestamp()),
                    new TransactionEntity("tx-2", credit.accountId(), credit.type(), credit.amount(), credit.description(), credit.timestamp())
            );
        });

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("200.00"),
                "Transfer test"
        );

        // Act
        List<TransactionResponse> result = bankAccountService.transfer(request);

        // Assert
        assertEquals(2, result.size());

        TransactionResponse debit = result.stream()
                .filter(tx -> tx.accountId().equals("acc-1"))
                .findFirst()
                .orElseThrow();
        TransactionResponse credit = result.stream()
                .filter(tx -> tx.accountId().equals("acc-2"))
                .findFirst()
                .orElseThrow();

        assertEquals("DEBIT", debit.type());
        assertEquals(new BigDecimal("200.00"), debit.amount());
        assertEquals("CREDIT", credit.type());
        assertEquals(new BigDecimal("200.00"), credit.amount());

        verify(transactionRepository, times(1)).saveAll(any(List.class));

        assertEquals(800.0, source.getBalance());
        assertEquals(200.0, target.getBalance());
        verify(bankAccountRepository).save(source);
        verify(bankAccountRepository).save(target);
    }

    @Test
    void testTransfer_sourceAccountNotOwnedByCurrentUser_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", "other-user", "EUR", false, 1000.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Unauthorized transfer"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    
    @Test
    void testTransfer_insufficientFunds_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 50.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("200.00"),
                "Too much"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_sameSourceAndTarget_throwsIllegalArgumentException() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-1",
                new BigDecimal("100.00"),
                "Self transfer"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    
    @Test
    void testTransfer_currencyMismatch_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 100.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "USD", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("50.00"),
                "FX transfer"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_sourceAccountNotFound_throwsEntityNotFoundException() {
        // Arrange
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.empty());

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Missing source"
        );

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_targetAccountNotFound_throwsEntityNotFoundException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 1000.0);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.empty());

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Missing target"
        );

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_sourceAccountDeleted_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", true, 1000.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Source closed"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_targetAccountDeleted_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 1000.0);
        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", true, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("100.00"),
                "Target closed"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }
}
