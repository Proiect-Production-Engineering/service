package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.AccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.response.BalanceSheetEntry;
import ro.unibuc.prodeng.response.BalanceSheetResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class ReportingServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private ReportingService reportingService;

    private final AccountEntity testAccount = new AccountEntity("acc1", "user1", "Main Account", "RON");

    @Test
    void testGetBalanceSheet_accountWithNoTransactions_returnsZeroBalance() {
        // Arrange
        when(accountService.getEntityById("acc1")).thenReturn(testAccount);
        when(transactionRepository.findByAccountIdOrderByTimestampAsc("acc1")).thenReturn(Collections.emptyList());

        // Act
        BalanceSheetResponse result = reportingService.getBalanceSheet("acc1");

        // Assert
        assertNotNull(result);
        assertEquals("acc1", result.accountId());
        assertEquals("Main Account", result.accountName());
        assertEquals("RON", result.currency());
        assertEquals(BigDecimal.ZERO, result.currentBalance());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void testGetBalanceSheet_accountWithSingleCredit_returnsPositiveBalance() {
        // Arrange
        Instant now = Instant.now();
        TransactionEntity credit = new TransactionEntity("tx1", "acc1", TransactionType.CREDIT, new BigDecimal("500.00"), "Initial deposit", now);
        when(accountService.getEntityById("acc1")).thenReturn(testAccount);
        when(transactionRepository.findByAccountIdOrderByTimestampAsc("acc1")).thenReturn(List.of(credit));

        // Act
        BalanceSheetResponse result = reportingService.getBalanceSheet("acc1");

        // Assert
        assertEquals(1, result.entries().size());
        assertEquals(new BigDecimal("500.00"), result.currentBalance());
        assertEquals(new BigDecimal("500.00"), result.entries().getFirst().runningBalance());
    }

    @Test
    void testGetBalanceSheet_accountWithCreditAndDebit_computesRunningBalanceCorrectly() {
        // Arrange
        Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-02T10:00:00Z");
        Instant t3 = Instant.parse("2025-01-03T10:00:00Z");

        List<TransactionEntity> transactions = List.of(
                new TransactionEntity("tx1", "acc1", TransactionType.CREDIT, new BigDecimal("1000.00"), "Salary", t1),
                new TransactionEntity("tx2", "acc1", TransactionType.DEBIT, new BigDecimal("200.00"), "Rent", t2),
                new TransactionEntity("tx3", "acc1", TransactionType.CREDIT, new BigDecimal("50.00"), "Refund", t3)
        );

        when(accountService.getEntityById("acc1")).thenReturn(testAccount);
        when(transactionRepository.findByAccountIdOrderByTimestampAsc("acc1")).thenReturn(transactions);

        // Act
        BalanceSheetResponse result = reportingService.getBalanceSheet("acc1");

        // Assert
        assertEquals(3, result.entries().size());

        assertEquals(new BigDecimal("1000.00"), result.entries().get(0).runningBalance());
        assertEquals("CREDIT", result.entries().get(0).type());

        assertEquals(new BigDecimal("800.00"), result.entries().get(1).runningBalance());
        assertEquals("DEBIT", result.entries().get(1).type());

        assertEquals(new BigDecimal("850.00"), result.entries().get(2).runningBalance());
        assertEquals(new BigDecimal("850.00"), result.currentBalance());
    }

    @Test
    void testGetBalanceSheet_allDebits_resultsInNegativeBalance() {
        // Arrange
        Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2025-01-02T10:00:00Z");

        List<TransactionEntity> transactions = List.of(
                new TransactionEntity("tx1", "acc1", TransactionType.DEBIT, new BigDecimal("100.00"), "Fee", t1),
                new TransactionEntity("tx2", "acc1", TransactionType.DEBIT, new BigDecimal("50.00"), "Penalty", t2)
        );

        when(accountService.getEntityById("acc1")).thenReturn(testAccount);
        when(transactionRepository.findByAccountIdOrderByTimestampAsc("acc1")).thenReturn(transactions);

        // Act
        BalanceSheetResponse result = reportingService.getBalanceSheet("acc1");

        // Assert
        assertEquals(new BigDecimal("-150.00"), result.currentBalance());
        assertEquals(new BigDecimal("-100.00"), result.entries().get(0).runningBalance());
        assertEquals(new BigDecimal("-150.00"), result.entries().get(1).runningBalance());
    }

    @Test
    void testGetBalanceSheet_nonExistentAccount_throwsEntityNotFoundException() {
        // Arrange
        when(accountService.getEntityById("nonexistent")).thenThrow(new EntityNotFoundException("nonexistent"));

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> reportingService.getBalanceSheet("nonexistent"));
        verify(transactionRepository, never()).findByAccountIdOrderByTimestampAsc(anyString());
    }

    @Test
    void testComputeRunningBalance_emptyTransactionList_returnsEmptyList() {
        // Act
        List<BalanceSheetEntry> entries = reportingService.computeRunningBalance(Collections.emptyList());

        // Assert
        assertTrue(entries.isEmpty());
    }

    @Test
    void testGetBalanceSheet_preservesTransactionMetadata() {
        // Arrange
        Instant timestamp = Instant.parse("2025-06-15T14:30:00Z");
        TransactionEntity tx = new TransactionEntity("tx-abc", "acc1", TransactionType.CREDIT, new BigDecimal("999.99"), "Bonus payment", timestamp);

        when(accountService.getEntityById("acc1")).thenReturn(testAccount);
        when(transactionRepository.findByAccountIdOrderByTimestampAsc("acc1")).thenReturn(List.of(tx));

        // Act
        BalanceSheetResponse result = reportingService.getBalanceSheet("acc1");

        // Assert
        BalanceSheetEntry entry = result.entries().getFirst();
        assertEquals("tx-abc", entry.transactionId());
        assertEquals(timestamp, entry.timestamp());
        assertEquals("Bonus payment", entry.description());
        assertEquals("CREDIT", entry.type());
        assertEquals(new BigDecimal("999.99"), entry.amount());
    }
}
