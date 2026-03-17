package ro.unibuc.prodeng.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ro.unibuc.prodeng.model.TransactionEntity;

@Repository
public interface TransactionRepository extends MongoRepository<TransactionEntity, String> {
    List<TransactionEntity> findByAccountIdOrderByTimestampAsc(String accountId);
}
