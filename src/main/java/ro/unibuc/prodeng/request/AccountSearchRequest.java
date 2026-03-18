package ro.unibuc.prodeng.request;

public record AccountSearchRequest(
    String iban,
    String ownerName,
    Integer page,
    Integer size
) {}
