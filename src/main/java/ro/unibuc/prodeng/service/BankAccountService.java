package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
import ro.unibuc.prodeng.request.CreateTransactionRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;

@Service
public class BankAccountService {

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private CountryService countryService;

    @Autowired
    private CurrencyService currencyService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IBANService ibanService;

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
                .balance(0.0)
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

        account.setDeleted(true);
        bankAccountRepository.save(account);
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        getEntityById(request.accountId());
        TransactionType type = TransactionType.valueOf(request.type().toUpperCase());
        TransactionEntity entity = new TransactionEntity(
                null,
                request.accountId(),
                type,
                request.amount(),
                request.description(),
                Instant.now()
        );
        TransactionEntity saved = transactionRepository.save(entity);
        return toTransactionResponse(saved);
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
