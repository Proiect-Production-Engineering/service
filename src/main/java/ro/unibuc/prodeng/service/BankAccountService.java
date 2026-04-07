package ro.unibuc.prodeng.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ro.unibuc.prodeng.config.ApplicationConfig;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CountryService countryService;
    private final CurrencyService currencyService;
    private final TransactionRepository transactionRepository;
    private final IBANService ibanService;

    public BankAccountResponse createAccount(CreateBankAccountRequest request) {
        String normalizedCurrency = request.currencyCode().toUpperCase();

        // Validate currency exists
        if (!currencyService.existsByCode(normalizedCurrency)) {
            throw new IllegalArgumentException("Unsupported currency: " + request.currencyCode());
        }

        // Validate country exists and get its IBAN pattern
        CountryEntity country = countryService.getCountryEntityByCode(request.countryCode());
        if (country.ibanPattern() == null || country.ibanPattern().isBlank()) {
            throw new IllegalArgumentException("Country " + request.countryCode() + " does not have an IBAN pattern configured");
        }

        // Get current authenticated user
        String userId = getCurrentUserId();

        // Check max accounts limit
        long activeAccountCount = bankAccountRepository.countByUserIdAndDeletedFalse(userId);
        if (activeAccountCount >= ApplicationConfig.MAX_ACCOUNTS_PER_USER) {
            throw new IllegalArgumentException(
                    "Maximum number of accounts reached (" + ApplicationConfig.MAX_ACCOUNTS_PER_USER + ")");
        }

        // Check duplicate currency
        if (bankAccountRepository.existsByUserIdAndCurrencyCodeAndDeletedFalse(userId, normalizedCurrency)) {
            throw new IllegalArgumentException("You already have an active account with currency: " + normalizedCurrency);
        }

        // Generate a unique IBAN
        String iban = generateUniqueIBAN(country.code(), country.ibanPattern());

        BankAccountEntity account = BankAccountEntity.builder()
                .iban(iban)
                .userId(userId)
                .currencyCode(normalizedCurrency)
                .countryCode(country.code())
                .accountHolderName(request.accountHolderName())
                .balance(BigDecimal.ZERO)
                .deleted(false)
                .build();

        BankAccountEntity saved = bankAccountRepository.save(account);
        return toResponse(saved);
    }

    public List<BankAccountResponse> getMyAccounts() {
        String userId = getCurrentUserId();
        return bankAccountRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public BankAccountResponse getAccountById(String id) {
        BankAccountEntity account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        return toResponse(account);
    }

    public BankAccountResponse getAccountByIban(String iban) {
        BankAccountEntity account = bankAccountRepository.findByIban(iban)
                .orElseThrow(() -> new EntityNotFoundException(iban));
        return toResponse(account);
    }

    public Page<BankAccountResponse> getAllAccounts(Pageable pageable) {
        return bankAccountRepository.findByDeletedFalse(pageable)
                .map(this::toResponse);
    }

    public List<BankAccountResponse> getAccountsByUserId(String userId) {
        return bankAccountRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public void closeAccount(String id) {
        BankAccountEntity account = bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));

        if (account.isDeleted()) {
            throw new IllegalArgumentException("Account is already closed");
        }

        BigDecimal balance = getEffectiveBalance(account);
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Account balance must be zero before closing (current balance: " + balance + ")");
        }

        account.setDeleted(true);
        bankAccountRepository.save(account);
        log.info("Account {} closed", id);
    }

    @Transactional
    public List<TransactionResponse> transfer(CreateTransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new IllegalArgumentException("Source and target accounts must be different");
        }

        BigDecimal amount = request.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        String currentUserId = getCurrentUserId();

        BankAccountEntity sourceAccount = bankAccountRepository.findById(request.sourceAccountId())
                .orElseThrow(() -> new EntityNotFoundException(request.sourceAccountId()));
        BankAccountEntity targetAccount = bankAccountRepository.findById(request.targetAccountId())
                .orElseThrow(() -> new EntityNotFoundException(request.targetAccountId()));

        if (!sourceAccount.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("Source account does not belong to the current user");
        }

        if (sourceAccount.isDeleted()) {
            throw new IllegalArgumentException("Source account is closed");
        }

        if (targetAccount.isDeleted()) {
            throw new IllegalArgumentException("Target account is closed");
        }

        if (!sourceAccount.getCurrencyCode().equals(targetAccount.getCurrencyCode())) {
            throw new IllegalArgumentException("Source and target accounts must have the same currency");
        }

        BigDecimal sourceBalance = getEffectiveBalance(sourceAccount);
        if (sourceBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in source account");
        }

        Instant now = Instant.now();
        String description = request.description();

        // Update stored balances atomically within the transaction
        BigDecimal targetBalance = getEffectiveBalance(targetAccount);

        BigDecimal updatedSource = sourceBalance.subtract(amount);
        BigDecimal updatedTarget = targetBalance.add(amount);

        sourceAccount.setBalance(updatedSource);
        targetAccount.setBalance(updatedTarget);

        bankAccountRepository.saveAll(List.of(sourceAccount, targetAccount));

        log.info("Transfer of {} from account {} to account {} completed", amount, sourceAccount.getId(), targetAccount.getId());

        TransactionEntity debit = new TransactionEntity(
                null,
                sourceAccount.getId(),
                TransactionType.DEBIT,
                amount,
                description,
                now
        );

        TransactionEntity credit = new TransactionEntity(
                null,
                targetAccount.getId(),
                TransactionType.CREDIT,
                amount,
                description,
                now
        );

        List<TransactionEntity> saved = transactionRepository.saveAll(List.of(debit, credit));
        return saved.stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    public BankAccountEntity getEntityById(String id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
    }

    private String generateUniqueIBAN(String countryCode, String ibanPattern) {
        String iban;
        int maxAttempts = 10;
        int attempt = 0;
        do {
            iban = ibanService.generateIBAN(countryCode, ibanPattern);
            attempt++;
            if (attempt > maxAttempts) {
                throw new IllegalStateException("Failed to generate unique IBAN after " + maxAttempts + " attempts");
            }
        } while (bankAccountRepository.existsByIban(iban));
        return iban;
    }

    private BigDecimal getEffectiveBalance(BankAccountEntity account) {
        return account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userDetails.getId();
    }

    private BankAccountResponse toResponse(BankAccountEntity entity) {
        return new BankAccountResponse(
                entity.getId(),
                entity.getIban(),
                entity.getUserId(),
                entity.getCurrencyCode(),
                entity.getCountryCode(),
                entity.getAccountHolderName(),
                entity.getBalance(),
                entity.isDeleted()
        );
    }

    private TransactionResponse toTransactionResponse(TransactionEntity entity) {
        return new TransactionResponse(
                entity.id(),
                entity.accountId(),
                entity.type().name(),
                entity.amount(),
                entity.description(),
                entity.timestamp()
        );
    }
}
