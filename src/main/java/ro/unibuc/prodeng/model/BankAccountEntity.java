package ro.unibuc.prodeng.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bank_accounts")
public class BankAccountEntity {
    @Id
    private String id;

    @Indexed(unique = true)
    private String iban;

    private String userId;

    private String currencyCode;

    private String countryCode;

    private String accountHolderName;

    @Builder.Default
    private Double balance = 0.0;

    @Builder.Default
    private boolean deleted = false;
}
