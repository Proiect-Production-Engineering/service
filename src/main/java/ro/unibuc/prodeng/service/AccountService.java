package ro.unibuc.prodeng.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.model.AccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.model.TransactionEntity.TransactionType;
import ro.unibuc.prodeng.repository.AccountRepository;
import ro.unibuc.prodeng.repository.TransactionRepository;
import ro.unibuc.prodeng.request.CreateAccountRequest;
import ro.unibuc.prodeng.request.CreateTransactionRequest;
import ro.unibuc.prodeng.response.AccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserService userService;

    public AccountResponse createAccount(CreateAccountRequest request) {
        userService.getUserEntityById(request.userId());
        AccountEntity entity = new AccountEntity(null, request.userId(), request.accountName(), request.currency());
        AccountEntity saved = accountRepository.save(entity);
        return toResponse(saved);
    }

    public AccountResponse getAccountById(String id) {
        AccountEntity entity = getEntityById(id);
        return toResponse(entity);
    }

    public List<AccountResponse> getAccountsByUserId(String userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
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

    public AccountEntity getEntityById(String id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
    }

    public void deleteAccount(String id) {
        if (!accountRepository.existsById(id)) {
            throw new EntityNotFoundException(id);
        }
        accountRepository.deleteById(id);
    }

    private AccountResponse toResponse(AccountEntity entity) {
        return new AccountResponse(entity.id(), entity.userId(), entity.accountName(), entity.currency());
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
