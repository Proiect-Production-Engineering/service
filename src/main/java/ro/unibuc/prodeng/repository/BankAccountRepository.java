package ro.unibuc.prodeng.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.BankAccountEntity;

@Repository
public interface BankAccountRepository extends MongoRepository<BankAccountEntity, String> {

    Optional<BankAccountEntity> findByIban(String iban);

    List<BankAccountEntity> findByUserId(String userId);

    List<BankAccountEntity> findByUserIdAndDeletedFalse(String userId);

    Page<BankAccountEntity> findByDeletedFalse(Pageable pageable);

    boolean existsByIban(String iban);

    boolean existsByUserIdAndCurrencyCodeAndDeletedFalse(String userId, String currencyCode);

    long countByUserIdAndDeletedFalse(String userId);

    long countByDeletedFalse();
}
