package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "countries")
public record CountryEntity(
    @Id String id,
    String name,
    @Indexed(unique = true) String code,
    String ibanPattern
) {}
