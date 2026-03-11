package ro.unibuc.prodeng.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ro.unibuc.prodeng.model.CurrencyExchangeRateEntity;

@Repository
public interface CurrencyExchangeRateRepository extends MongoRepository<CurrencyExchangeRateEntity, String> {

    Optional<CurrencyExchangeRateEntity> findBySourceCurrencyAndTargetCurrency(String sourceCurrency, String targetCurrency);
}
