package ro.unibuc.prodeng.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import ro.unibuc.prodeng.model.BankAccountEntity;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.repository.BankAccountRepository;
import ro.unibuc.prodeng.request.AccountSearchRequest;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.BankAccountResponse;
import ro.unibuc.prodeng.response.TransactionResponse;

@Service
public class AdminService {

    private final MongoTemplate mongoTemplate;
    private final BankAccountRepository bankAccountRepository;
    private final Counter adminSearchCounter;

    @Autowired
    public AdminService(MongoTemplate mongoTemplate, MeterRegistry meterRegistry,
                        BankAccountRepository bankAccountRepository) {
        this.mongoTemplate = mongoTemplate;
        this.bankAccountRepository = bankAccountRepository;
        this.adminSearchCounter = Counter.builder("admin.transactions.search.count")
                .description("Number of transaction searches performed by admin")
                .register(meterRegistry);
    }

    public Page<TransactionResponse> searchTransactions(TransactionSearchRequest request) {
        adminSearchCounter.increment();

        Query query = buildSearchQuery(request);

        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;
        Pageable pageable = PageRequest.of(page, size);

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TransactionEntity.class);

        query.with(pageable);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));

        List<TransactionEntity> results = mongoTemplate.find(query, TransactionEntity.class);
        List<TransactionResponse> mapped = results.stream().map(this::toResponse).toList();

        return PageableExecutionUtils.getPage(mapped, pageable, () -> total);
    }

    public Page<BankAccountResponse> searchAccounts(AccountSearchRequest request) {
        adminSearchCounter.increment();

        Query query = new Query();

        if (request.iban() != null && !request.iban().isBlank()) {
            query.addCriteria(Criteria.where("iban").regex(request.iban(), "i"));
        }
        if (request.ownerName() != null && !request.ownerName().isBlank()) {
            query.addCriteria(Criteria.where("accountHolderName").regex(request.ownerName(), "i"));
        }

        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? request.size() : 20;
        Pageable pageable = PageRequest.of(page, size);

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BankAccountEntity.class);

        query.with(pageable);

        List<BankAccountEntity> results = mongoTemplate.find(query, BankAccountEntity.class);
        List<BankAccountResponse> mapped = results.stream().map(this::toAccountResponse).toList();

        return PageableExecutionUtils.getPage(mapped, pageable, () -> total);
    }

    Query buildSearchQuery(TransactionSearchRequest request) {
        Query query = new Query();

        if (request.accountId() != null && !request.accountId().isBlank()) {
            query.addCriteria(Criteria.where("accountId").is(request.accountId()));
        }
        if (request.iban() != null && !request.iban().isBlank()) {
            bankAccountRepository.findByIban(request.iban()).ifPresent(account ->
                    query.addCriteria(Criteria.where("accountId").is(account.getId()))
            );
        }
        if (request.type() != null && !request.type().isBlank()) {
            query.addCriteria(Criteria.where("type").is(request.type().toUpperCase()));
        }
        if (request.descriptionKeyword() != null && !request.descriptionKeyword().isBlank()) {
            query.addCriteria(Criteria.where("description").regex(request.descriptionKeyword(), "i"));
        }
        if (request.from() != null && request.to() != null) {
            query.addCriteria(Criteria.where("timestamp").gte(request.from()).lte(request.to()));
        } else if (request.from() != null) {
            query.addCriteria(Criteria.where("timestamp").gte(request.from()));
        } else if (request.to() != null) {
            query.addCriteria(Criteria.where("timestamp").lte(request.to()));
        }
        if (request.minAmount() != null && request.maxAmount() != null) {
            query.addCriteria(Criteria.where("amount").gte(request.minAmount()).lte(request.maxAmount()));
        } else if (request.minAmount() != null) {
            query.addCriteria(Criteria.where("amount").gte(request.minAmount()));
        } else if (request.maxAmount() != null) {
            query.addCriteria(Criteria.where("amount").lte(request.maxAmount()));
        }

        return query;
    }

    private TransactionResponse toResponse(TransactionEntity entity) {
        return new TransactionResponse(
                entity.id(),
                entity.accountId(),
                entity.type().name(),
                entity.amount(),
                entity.description(),
                entity.timestamp()
        );
    }

    private BankAccountResponse toAccountResponse(BankAccountEntity entity) {
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
}
