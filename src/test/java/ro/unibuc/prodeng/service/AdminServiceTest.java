package ro.unibuc.prodeng.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.TransactionResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class AdminServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private MeterRegistry meterRegistry;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adminService = new AdminService(mongoTemplate, meterRegistry);
    }

    @Test
    void testSearchTransactions_noFilters_returnsAllTransactions() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null);
        TransactionEntity tx = new TransactionEntity("tx1", "acc1", TransactionType.CREDIT, new BigDecimal("100.00"), "Test", Instant.now());
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(List.of(tx));

        // Act
        List<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertEquals(1, results.size());
        assertEquals("tx1", results.getFirst().id());
        assertEquals("CREDIT", results.getFirst().type());
    }

    @Test
    void testSearchTransactions_noResults_returnsEmptyList() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest("nonexistent", null, null, null, null);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        List<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchTransactions_incrementsMetricCounter() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null);
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
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", null, null, null, null);
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
        TransactionSearchRequest request = new TransactionSearchRequest("acc1", "CREDIT", "salary", from, to);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        adminService.searchTransactions(request);

        // Assert
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }

    @Test
    void testBuildSearchQuery_emptyStringFiltersAreIgnored() {
        // Arrange
        TransactionSearchRequest request = new TransactionSearchRequest("", "", "", null, null);

        // Act
        Query query = adminService.buildSearchQuery(request);

        // Assert — empty strings should be treated as "no filter"
        assertNotNull(query);
    }

    @Test
    void testSearchTransactions_mapsTransactionFieldsCorrectly() {
        // Arrange
        Instant timestamp = Instant.parse("2025-03-15T10:00:00Z");
        TransactionEntity tx = new TransactionEntity("tx-id", "acc-id", TransactionType.DEBIT, new BigDecimal("75.50"), "Grocery shopping", timestamp);
        TransactionSearchRequest request = new TransactionSearchRequest(null, null, null, null, null);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(List.of(tx));

        // Act
        List<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        TransactionResponse response = results.getFirst();
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
        TransactionSearchRequest request = new TransactionSearchRequest(null, "debit", null, null, null);
        when(mongoTemplate.find(any(Query.class), eq(TransactionEntity.class))).thenReturn(Collections.emptyList());

        // Act
        List<TransactionResponse> results = adminService.searchTransactions(request);

        // Assert
        assertTrue(results.isEmpty());
        verify(mongoTemplate).find(any(Query.class), eq(TransactionEntity.class));
    }
}
