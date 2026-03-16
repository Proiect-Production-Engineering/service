package ro.unibuc.prodeng.response;

import java.util.List;

public record UserResponse(
    String id,
    String username,
    String name,
    String email,
    List<String> roles
) {}
