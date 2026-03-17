package ro.unibuc.prodeng.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import ro.unibuc.prodeng.model.TransactionEntity;
import ro.unibuc.prodeng.request.TransactionSearchRequest;
import ro.unibuc.prodeng.response.TransactionResponse;

@Service
public class AdminService {

    private final MongoTemplate mongoTemplate;
    private final Counter adminSearchCounter;

    @Autowired
    public AdminService(MongoTemplate mongoTemplate, MeterRegistry meterRegistry) {
        this.mongoTemplate = mongoTemplate;
        this.adminSearchCounter = Counter.builder("admin.transactions.search.count")
                .description("Number of transaction searches performed by admin")
                .register(meterRegistry);
    }

    public List<TransactionResponse> searchTransactions(TransactionSearchRequest request) {
        adminSearchCounter.increment();

        Query query = buildSearchQuery(request);
        List<TransactionEntity> results = mongoTemplate.find(query, TransactionEntity.class);

        return results.stream()
                .map(this::toResponse)
                .toList();
    }

    Query buildSearchQuery(TransactionSearchRequest request) {
        Query query = new Query();

        if (request.accountId() != null && !request.accountId().isBlank()) {
            query.addCriteria(Criteria.where("accountId").is(request.accountId()));
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
}
