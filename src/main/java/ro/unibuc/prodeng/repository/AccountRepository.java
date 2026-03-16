package ro.unibuc.prodeng.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ro.unibuc.prodeng.model.AccountEntity;

@Repository
public interface AccountRepository extends MongoRepository<AccountEntity, String> {
    List<AccountEntity> findByUserId(String userId);
}
