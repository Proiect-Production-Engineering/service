package ro.unibuc.prodeng.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import ro.unibuc.prodeng.model.RoleEntity;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;

@Service
public class UserService {

    private static final String ADMIN_USERNAME = "admin";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        UserEntity user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException(userDetails.getId()));
        return toResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getUserById(String id) throws EntityNotFoundException {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        return toResponse(user);
    }

    public UserEntity getUserEntityById(String id) throws EntityNotFoundException {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (ADMIN_USERNAME.equals(request.username())) {
            throw new IllegalArgumentException("Username 'admin' is reserved.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists: " + request.email());
        }
        UserEntity user = UserEntity.builder()
                .username(request.username())
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .roles(new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))))
                .build();
        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    public UserResponse changeName(String id, String newName) throws EntityNotFoundException {
        UserEntity existing = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        validateNotAdmin(existing);
        existing.setName(newName);
        UserEntity saved = userRepository.save(existing);
        return toResponse(saved);
    }

    public void deleteUser(String id) throws EntityNotFoundException {
        UserEntity existing = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
        validateNotAdmin(existing);
        userRepository.deleteById(id);
    }

    public UserResponse getUserByEmail(String email) throws EntityNotFoundException {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(email));
        return toResponse(user);
    }

    public UserEntity getUserEntityByEmail(String email) throws EntityNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(email));
    }

    private void validateNotAdmin(UserEntity user) {
        if (ADMIN_USERNAME.equals(user.getUsername())) {
            throw new IllegalArgumentException("The default administrator account cannot be altered.");
        }
    }

    private UserResponse toResponse(UserEntity user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(RoleEntity::getName).toList()
                : Collections.emptyList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                roleNames
        );
    }
}
