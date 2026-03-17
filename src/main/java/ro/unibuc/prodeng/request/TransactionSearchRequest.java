package ro.unibuc.prodeng.request;

import java.time.Instant;

public record TransactionSearchRequest(
    String accountId,
    String type,
    String descriptionKeyword,
    Instant from,
    Instant to
) {}
