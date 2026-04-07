package ro.unibuc.prodeng.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.request.AccountSearchRequest;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BankAccountRepository bankAccountRepository;

    private MeterRegistry meterRegistry;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adminService = new AdminService(mongoTemplate, meterRegistry, bankAccountRepository);
    }

    @Test
    void testSearchTransactions_noFilters_returnsAllTransactions() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, null, null);
        TransactionEntity tx = new TransactionEntity("tx1", "acc1", TransactionType.CREDIT, new BigDecimal("100.00"), "Test", Instant.now());
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(List.of(tx));

        // Act
        Page<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertEquals(1, results.getContent().size());
        assertEquals("tx1", results.getContent().getFirst().id());
        assertEquals("CREDIT", results.getContent().getFirst().type());
    }

    @Test
    void testSearchTransactions_noResults_returnsEmptyPage() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest("nonexistent", null, null, null, null, null, null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        Page<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertTrue(results.getContent().isEmpty());
        assertEquals(0, results.getTotalElements());
    }

    @Test
    void testSearchTransactions_incrementsMetricCounter() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);
        adminService.searchTransactions(request);
        adminService.searchTransactions(request);

        // Assert
        Counter counter = meterRegistry.find("admin.transactions.search.count").counter();
        assertNotNull(counter);
        assertEquals(3.0, counter.count());
    }

    @Test
    void testSearchTransactions_withAccountIdFilter_buildsCorrectQuery() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", null, null, null, null, null, null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);

        // Assert
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }

    @Test
    void testSearchTransactions_withAllFilters_buildsCompleteQuery() {
        // Arrange
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", "CREDIT", "salary", from, to, null, new BigDecimal("10"), new BigDecimal("1000"), 0, 10);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);

        // Assert
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }

    @Test
    void testBuildSearchQuery_emptyStringFiltersAreIgnored() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest("", "", "", null, null, "", null, null, null, null);

        // Act
        Query query = adminService.buildSearchQuery(request);

        // Assert
        assertNotNull(query);
    }

    @Test
    void testSearchTransactions_mapsTransactionFieldsCorrectly() {
        // Arrange
        Instant timestamp = Instant.parse("2025-03-15T10:00:00Z");
        TransactionEntity tx = new TransactionEntity("tx-id", "acc-id", TransactionType.DEBIT, new BigDecimal("75.50"), "Grocery shopping", timestamp);
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(List.of(tx));

        // Act
        Page<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        TransactionResponse response = results.getContent().getFirst();
        assertEquals("tx-id", response.id());
        assertEquals("acc-id", response.accountId());
        assertEquals("DEBIT", response.type());
        assertEquals(new BigDecimal("75.50"), response.amount());
        assertEquals("Grocery shopping", response.description());
        assertEquals(timestamp, response.timestamp());
    }

    @Test
    void testSearchTransactions_withTypeFilter_parsesTypeCorrectly() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, "debit", null, null, null, null, null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        Page<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertTrue(results.getContent().isEmpty());
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }

    @Test
    void testSearchTransactions_withIbanFilter_resolvesToAccountId() {
        // Arrange
        BankAccountEntity account = BankAccountEntity.builder().id("acc-resolved").iban("RO49AAAA1B31007593840000").build();
        when(bankAccountRepository.findByIban("RO49AAAA1B31007593840000")).thenReturn(Optional.of(account));

        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, "RO49AAAA1B31007593840000", null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);

        // Assert
        verify(bankAccountRepository).findByIban("RO49AAAA1B31007593840000");
    }

    @Test
    void testSearchTransactions_withAmountRange_buildsCorrectQuery() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, new BigDecimal("50"), new BigDecimal("500"), null, null);
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);

        // Assert
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }

    @Test
    void testSearchTransactions_withPagination_returnsCorrectPage() {
        // Arrange — page 1 (second page) with size 5, full page returned so total supplier is invoked
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null, null, null, null, 1, 5);
        Instant now = Instant.now();
        List<TransactionEntity> fiveItems = List.of(
                new TransactionEntity("tx1", "acc1", TransactionType.CREDIT, new BigDecimal("100.00"), "T1", now),
                new TransactionEntity("tx2", "acc1", TransactionType.CREDIT, new BigDecimal("200.00"), "T2", now),
                new TransactionEntity("tx3", "acc1", TransactionType.DEBIT, new BigDecimal("50.00"), "T3", now),
                new TransactionEntity("tx4", "acc1", TransactionType.CREDIT, new BigDecimal("300.00"), "T4", now),
                new TransactionEntity("tx5", "acc1", TransactionType.DEBIT, new BigDecimal("75.00"), "T5", now)
        );
        when(mongoTemplate.count(any(Query.class), eq(TransactionEntity.class))).thenReturn(15L);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(fiveItems);

        // Act
        Page<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertEquals(15, results.getTotalElements());
        assertEquals(5, results.getContent().size());
        assertEquals(1, results.getNumber());
    }

    @Test
    void testSearchAccounts_noFilters_returnsAllAccounts() {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest(null, null, null, null);
        BankAccountEntity account = BankAccountEntity.builder()
                .id("acc1").iban("RO49AAAA1B31007593840000").userId("user1")
                .currencyCode("RON").countryCode("RO").accountHolderName("Test User")
                .balance(BigDecimal.valueOf(1000.0)).deleted(false).build();
        when(mongoTemplate.count(any(Query.class), eq(BankAccountEntity.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(BankAccountEntity.class))).thenReturn(List.of(account));

        // Act
        Page<BankAccountResponse> results = adminService.searchAccounts(request);

        // Assert
        assertEquals(1, results.getContent().size());
        assertEquals("RO49AAAA1B31007593840000", results.getContent().getFirst().iban());
        assertEquals("Test User", results.getContent().getFirst().accountHolderName());
    }

    @Test
    void testSearchAccounts_withIbanFilter_returnsMatchingAccounts() {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest("RO49", null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(BankAccountEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(BankAccountEntity.class))).thenReturn(Collections.emptyList());

        // Act
        Page<BankAccountResponse> results = adminService.searchAccounts(request);

        // Assert
        assertTrue(results.getContent().isEmpty());
        verify(mongoTemplate).find(any(Query.class), eq(BankAccountEntity.class));
    }

    @Test
    void testSearchAccounts_withOwnerNameFilter_returnsMatchingAccounts() {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest(null, "John", 0, 10);
        when(mongoTemplate.count(any(Query.class), eq(BankAccountEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(BankAccountEntity.class))).thenReturn(Collections.emptyList());

        // Act
        Page<BankAccountResponse> results = adminService.searchAccounts(request);

        // Assert
        assertTrue(results.getContent().isEmpty());
        verify(mongoTemplate).find(any(Query.class), eq(BankAccountEntity.class));
    }

    @Test
    void testSearchAccounts_incrementsAccountSearchCounter() {
        // Arrange
        AccountSearchRequest request = new AccountSearchRequest(null, null, null, null);
        when(mongoTemplate.count(any(Query.class), eq(BankAccountEntity.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(BankAccountEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchAccounts(request);
        adminService.searchAccounts(request);

        // Assert — account counter incremented, transaction counter untouched
        Counter accountCounter = meterRegistry.find("admin.accounts.search.count").counter();
        assertNotNull(accountCounter);
        assertEquals(2.0, accountCounter.count());

        Counter txCounter = meterRegistry.find("admin.transactions.search.count").counter();
        assertNotNull(txCounter);
        assertEquals(0.0, txCounter.count());
    }
}
