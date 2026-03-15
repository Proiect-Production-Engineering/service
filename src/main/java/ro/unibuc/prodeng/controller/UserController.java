package ro.unibuc.prodeng.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import ro.unibuc.prodeng.request.ChangeNameRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.service.UserService;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
@SecurityRequirement(name = "Authentication")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    @Operation(summary = "View the currently authenticated user's account")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UserResponse user = userService.getCurrentUser();
        return ResponseEntity.ok(user);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users (admin only)")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a user by ID (admin only)")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) throws EntityNotFoundException {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user (admin only)")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user's name (admin only)")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String id,
            @Valid @RequestBody ChangeNameRequest request) throws EntityNotFoundException {
        UserResponse user = userService.changeName(id, request.name());
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{id}/name")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Change a user's name (admin only)")
    public ResponseEntity<UserResponse> changeName(
            @PathVariable String id,
            @Valid @RequestBody ChangeNameRequest request) throws EntityNotFoundException {
        UserResponse user = userService.changeName(id, request.name());
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user (admin only)")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) throws EntityNotFoundException {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a user by email (admin only)")
    public ResponseEntity<UserResponse> getUserByEmail(@RequestParam String email)
            throws EntityNotFoundException {
        UserResponse user = userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }
}
