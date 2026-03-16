package ro.unibuc.prodeng.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import ro.unibuc.prodeng.model.CountryEntity;

@Repository
public interface CountryRepository extends MongoRepository<CountryEntity, String> {

    Optional<CountryEntity> findByCode(String code);

    boolean existsByCode(String code);
}
