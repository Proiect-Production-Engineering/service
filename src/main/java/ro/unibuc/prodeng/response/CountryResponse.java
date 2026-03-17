package ro.unibuc.prodeng.response;

public record CountryResponse(
    String id,
    String name,
    String code,
    String ibanPattern
) {}
