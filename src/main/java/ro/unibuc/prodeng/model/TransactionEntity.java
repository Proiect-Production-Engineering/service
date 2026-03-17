package ro.unibuc.prodeng.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "transactions")
public record TransactionEntity(
    @Id String id,
    @Indexed String accountId,
    TransactionType type,
    BigDecimal amount,
    String description,
    Instant timestamp
) {
    public enum TransactionType {
        CREDIT, DEBIT
    }
}
