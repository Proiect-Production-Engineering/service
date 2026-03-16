package ro.unibuc.prodeng.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.CurrencyEntity;

@Repository
public interface CurrencyRepository extends MongoRepository<CurrencyEntity, String> {

    Optional<CurrencyEntity> findByCode(String code);

    boolean existsByCode(String code);
}
