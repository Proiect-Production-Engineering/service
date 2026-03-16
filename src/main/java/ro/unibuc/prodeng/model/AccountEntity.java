package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "accounts")
public record AccountEntity(
    @Id String id,
    @Indexed String userId,
    String accountName,
    String currency
) {}
