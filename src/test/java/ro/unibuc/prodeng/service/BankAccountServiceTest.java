package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.CountryEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.request.CreateBankAccountRequest;
import ro.unibuc.prodeng.request.CreateTransferRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CountryService countryService;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private IBANService ibanService;

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

        private BankAccountEntity makeAccount(String id, String userId, String currency, boolean deleted, double balance) {
        BankAccountEntity account = new BankAccountEntity();
        account.setId(id);
        account.setUserId(userId);
        account.setCurrencyCode(currency);
        account.setDeleted(deleted);
        account.setBalance(BigDecimal.valueOf(balance));
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

        assertEquals(0, new BigDecimal("800.00").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(target.getBalance()));
        verify(bankAccountRepository).saveAll(any(List.class));
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

    @Test
    void testTransfer_zeroAmount_throwsIllegalArgumentException() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                BigDecimal.ZERO,
                "Zero amount"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Test
    void testTransfer_negativeAmount_throwsIllegalArgumentException() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("-10.00"),
                "Negative amount"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Test
    void testTransfer_nullAmount_throwsIllegalArgumentException() {
        // Arrange
        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                null,
                "Null amount"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verifyNoInteractions(bankAccountRepository, transactionRepository);
    }

    @Test
    void testTransfer_sourceBalanceNull_treatedAsZeroAndInsufficientFunds() {
        // Arrange
        BankAccountEntity source = new BankAccountEntity();
        source.setId("acc-1");
        source.setUserId(CURRENT_USER_ID);
        source.setCurrencyCode("EUR");
        source.setDeleted(false);
        source.setBalance(null);

        BankAccountEntity target = makeAccount("acc-2", "user-2", "EUR", false, 0.0);

        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(source));
        when(bankAccountRepository.findById("acc-2")).thenReturn(Optional.of(target));

        CreateTransferRequest request = new CreateTransferRequest(
                "acc-1",
                "acc-2",
                new BigDecimal("50.00"),
                "Null source balance"
        );

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.transfer(request));
        verify(transactionRepository, never()).saveAll(any(List.class));
    }

    @Test
    void testTransfer_targetBalanceNull_treatedAsZeroAndUpdated() {
        // Arrange
        BankAccountEntity source = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 1000.0);

        BankAccountEntity target = new BankAccountEntity();
        target.setId("acc-2");
        target.setUserId("user-2");
        target.setCurrencyCode("EUR");
        target.setDeleted(false);
        target.setBalance(null);

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
                "Null target balance"
        );

        // Act
        List<TransactionResponse> result = bankAccountService.transfer(request);

        // Assert
        assertEquals(2, result.size());
        assertEquals(0, new BigDecimal("800.00").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("200.00").compareTo(target.getBalance()));
        verify(bankAccountRepository).saveAll(any(List.class));
        verify(transactionRepository).saveAll(any(List.class));
    }

    // ========================
    // createAccount tests
    // ========================

    @Test
    void testCreateAccount_validRequest_createsAndReturnsAccount() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("eur", "ro", "John Doe");
        when(currencyService.existsByCode("EUR")).thenReturn(true);
        when(countryService.getCountryEntityByCode("ro"))
                .thenReturn(new CountryEntity("c1", "Romania", "RO", "aaaacccccccccccccccc"));
        when(bankAccountRepository.countByUserIdAndDeletedFalse(CURRENT_USER_ID)).thenReturn(0L);
        when(bankAccountRepository.existsByUserIdAndCurrencyCodeAndDeletedFalse(CURRENT_USER_ID, "EUR")).thenReturn(false);
        when(ibanService.generateIBAN("RO", "aaaacccccccccccccccc")).thenReturn("RO49AAAA1234567890123456");
        when(bankAccountRepository.existsByIban("RO49AAAA1234567890123456")).thenReturn(false);
        when(bankAccountRepository.save(any(BankAccountEntity.class))).thenAnswer(invocation -> {
            BankAccountEntity entity = invocation.getArgument(0);
            entity.setId("acc-gen");
            return entity;
        });

        // Act
        BankAccountResponse result = bankAccountService.createAccount(request);

        // Assert
        assertNotNull(result);
        assertEquals("acc-gen", result.id());
        assertEquals("RO49AAAA1234567890123456", result.iban());
        assertEquals("EUR", result.currencyCode());
        assertEquals("John Doe", result.accountHolderName());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.balance()));
        assertFalse(result.deleted());
    }

    @Test
    void testCreateAccount_unsupportedCurrency_throwsIllegalArgumentException() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("xyz", "ro", "John Doe");
        when(currencyService.existsByCode("XYZ")).thenReturn(false);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.createAccount(request));
        assertTrue(ex.getMessage().contains("Unsupported currency"));
    }

    @Test
    void testCreateAccount_countryWithoutIbanPattern_throwsIllegalArgumentException() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("eur", "xx", "John Doe");
        when(currencyService.existsByCode("EUR")).thenReturn(true);
        when(countryService.getCountryEntityByCode("xx"))
                .thenReturn(new CountryEntity("c1", "Unknown", "XX", null));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.createAccount(request));
    }

    @Test
    void testCreateAccount_blankIbanPattern_throwsIllegalArgumentException() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("eur", "xx", "John Doe");
        when(currencyService.existsByCode("EUR")).thenReturn(true);
        when(countryService.getCountryEntityByCode("xx"))
                .thenReturn(new CountryEntity("c1", "Unknown", "XX", "   "));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> bankAccountService.createAccount(request));
    }

    @Test
    void testCreateAccount_maxAccountsReached_throwsIllegalArgumentException() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("eur", "ro", "John Doe");
        when(currencyService.existsByCode("EUR")).thenReturn(true);
        when(countryService.getCountryEntityByCode("ro"))
                .thenReturn(new CountryEntity("c1", "Romania", "RO", "aaaacccccccccccccccc"));
        when(bankAccountRepository.countByUserIdAndDeletedFalse(CURRENT_USER_ID)).thenReturn(3L);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.createAccount(request));
        assertTrue(ex.getMessage().contains("Maximum number of accounts"));
    }

    @Test
    void testCreateAccount_duplicateCurrency_throwsIllegalArgumentException() {
        // Arrange
        CreateBankAccountRequest request = new CreateBankAccountRequest("eur", "ro", "John Doe");
        when(currencyService.existsByCode("EUR")).thenReturn(true);
        when(countryService.getCountryEntityByCode("ro"))
                .thenReturn(new CountryEntity("c1", "Romania", "RO", "aaaacccccccccccccccc"));
        when(bankAccountRepository.countByUserIdAndDeletedFalse(CURRENT_USER_ID)).thenReturn(1L);
        when(bankAccountRepository.existsByUserIdAndCurrencyCodeAndDeletedFalse(CURRENT_USER_ID, "EUR")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.createAccount(request));
        assertTrue(ex.getMessage().contains("already have an active account"));
    }

    // ========================
    // getMyAccounts tests
    // ========================

    @Test
    void testGetMyAccounts_withAccounts_returnsCurrentUserAccounts() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 100.0);
        acc.setIban("RO49AAAA");
        acc.setCountryCode("RO");
        acc.setAccountHolderName("John");
        when(bankAccountRepository.findByUserIdAndDeletedFalse(CURRENT_USER_ID)).thenReturn(List.of(acc));

        // Act
        List<BankAccountResponse> result = bankAccountService.getMyAccounts();

        // Assert
        assertEquals(1, result.size());
        assertEquals("acc-1", result.get(0).id());
    }

    @Test
    void testGetMyAccounts_noAccounts_returnsEmptyList() {
        // Arrange
        when(bankAccountRepository.findByUserIdAndDeletedFalse(CURRENT_USER_ID)).thenReturn(Collections.emptyList());

        // Act
        List<BankAccountResponse> result = bankAccountService.getMyAccounts();

        // Assert
        assertTrue(result.isEmpty());
    }

    // ========================
    // getAccountById tests
    // ========================

    @Test
    void testGetAccountById_existingAccount_returnsAccount() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 500.0);
        acc.setIban("RO49AAAA");
        acc.setCountryCode("RO");
        acc.setAccountHolderName("John");
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act
        BankAccountResponse result = bankAccountService.getAccountById("acc-1");

        // Assert
        assertEquals("acc-1", result.id());
        assertEquals(0, BigDecimal.valueOf(500.0).compareTo(result.balance()));
    }

    @Test
    void testGetAccountById_nonExisting_throwsEntityNotFoundException() {
        // Arrange
        when(bankAccountRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.getAccountById("999"));
    }

    // ========================
    // getAccountByIban tests
    // ========================

    @Test
    void testGetAccountByIban_existingIban_returnsAccount() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 300.0);
        acc.setIban("RO49AAAA");
        acc.setCountryCode("RO");
        acc.setAccountHolderName("John");
        when(bankAccountRepository.findByIban("RO49AAAA")).thenReturn(Optional.of(acc));

        // Act
        BankAccountResponse result = bankAccountService.getAccountByIban("RO49AAAA");

        // Assert
        assertEquals("RO49AAAA", result.iban());
    }

    @Test
    void testGetAccountByIban_nonExistingIban_throwsEntityNotFoundException() {
        // Arrange
        when(bankAccountRepository.findByIban("INVALID")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.getAccountByIban("INVALID"));
    }

    // ========================
    // getAllAccounts tests
    // ========================

    @Test
    void testGetAllAccounts_withPagination_returnsPage() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 100.0);
        acc.setIban("RO49AAAA");
        acc.setCountryCode("RO");
        acc.setAccountHolderName("John");
        Pageable pageable = PageRequest.of(0, 10);
        Page<BankAccountEntity> page = new PageImpl<>(List.of(acc), pageable, 1);
        when(bankAccountRepository.findByDeletedFalse(pageable)).thenReturn(page);

        // Act
        Page<BankAccountResponse> result = bankAccountService.getAllAccounts(pageable);

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("acc-1", result.getContent().get(0).id());
    }

    // ========================
    // getAccountsByUserId tests
    // ========================

    @Test
    void testGetAccountsByUserId_withAccounts_returnsList() {
        // Arrange — includes a soft-deleted account (admin view should show all)
        BankAccountEntity acc = makeAccount("acc-1", "user-x", "EUR", false, 200.0);
        acc.setIban("RO49AAAA");
        acc.setCountryCode("RO");
        acc.setAccountHolderName("Jane");
        BankAccountEntity deleted = makeAccount("acc-2", "user-x", "GBP", true, 0.0);
        deleted.setIban("RO49BBBB");
        deleted.setCountryCode("RO");
        deleted.setAccountHolderName("Jane");
        when(bankAccountRepository.findByUserId("user-x")).thenReturn(List.of(acc, deleted));

        // Act
        List<BankAccountResponse> result = bankAccountService.getAccountsByUserId("user-x");

        // Assert — both active and deleted accounts are returned
        assertEquals(2, result.size());
        assertEquals("user-x", result.get(0).userId());
    }

    @Test
    void testGetAccountsByUserId_noAccounts_returnsEmptyList() {
        // Arrange
        when(bankAccountRepository.findByUserId("user-x")).thenReturn(Collections.emptyList());

        // Act
        List<BankAccountResponse> result = bankAccountService.getAccountsByUserId("user-x");

        // Assert
        assertTrue(result.isEmpty());
    }

    // ========================
    // closeAccount tests
    // ========================

    @Test
    void testCloseAccount_activeAccountWithZeroBalance_marksAsDeleted() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 0.0);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act
        bankAccountService.closeAccount("acc-1");

        // Assert
        assertTrue(acc.isDeleted());
        verify(bankAccountRepository).save(acc);
    }

    @Test
    void testCloseAccount_activeAccountWithNonZeroBalance_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 100.0);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.closeAccount("acc-1"));
        assertTrue(ex.getMessage().contains("balance must be zero"));
    }

    @Test
    void testCloseAccount_nullBalance_treatedAsZero_marksAsDeleted() {
        // Arrange
        BankAccountEntity acc = new BankAccountEntity();
        acc.setId("acc-1");
        acc.setUserId(CURRENT_USER_ID);
        acc.setCurrencyCode("EUR");
        acc.setDeleted(false);
        acc.setBalance(null);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act
        bankAccountService.closeAccount("acc-1");

        // Assert
        assertTrue(acc.isDeleted());
        verify(bankAccountRepository).save(acc);
    }

    @Test
    void testCloseAccount_alreadyClosed_throwsIllegalArgumentException() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", true, 0.0);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bankAccountService.closeAccount("acc-1"));
        assertEquals("Account is already closed", ex.getMessage());
    }

    @Test
    void testCloseAccount_nonExisting_throwsEntityNotFoundException() {
        // Arrange
        when(bankAccountRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.closeAccount("999"));
    }

    // ========================
    // getEntityById tests
    // ========================

    @Test
    void testGetEntityById_existingId_returnsEntity() {
        // Arrange
        BankAccountEntity acc = makeAccount("acc-1", CURRENT_USER_ID, "EUR", false, 100.0);
        when(bankAccountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        // Act
        BankAccountEntity result = bankAccountService.getEntityById("acc-1");

        // Assert
        assertEquals("acc-1", result.getId());
    }

    @Test
    void testGetEntityById_nonExisting_throwsEntityNotFoundException() {
        // Arrange
        when(bankAccountRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> bankAccountService.getEntityById("999"));
    }
}
