package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.RoleEntity;
import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.exception.EntityNotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserEntity makeUser(String id, String username, String name, String email) {
        return new UserEntity(id, username, name, email, "hashed", new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- getCurrentUser ---

    @Test
    void testGetCurrentUser_authenticatedUser_returnsCurrentUser() {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        UserDetails userDetails = UserDetails.build(user);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getCurrentUser();

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("alice", result.username());
        assertEquals("Alice", result.name());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testGetCurrentUser_userDeletedAfterAuth_throwsEntityNotFoundException() {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        UserDetails userDetails = UserDetails.build(user);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById("1")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getCurrentUser());
    }

    // --- getAllUsers ---

    @Test
    void testGetAllUsers_withMultipleUsers_returnsAllUsers() {
        // Arrange
        List<UserEntity> users = Arrays.asList(
                makeUser("1", "alice", "Alice", "alice@example.com"),
                makeUser("2", "bob", "Bob", "bob@example.com")
        );
        when(userRepository.findAll()).thenReturn(users);

        // Act
        List<UserResponse> result = userService.getAllUsers();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).name());
        assertEquals("Bob", result.get(1).name());
    }

    @Test
    void testGetUserById_existingUserRequested_returnsUser() throws EntityNotFoundException {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getUserById("1");

        // Assert
        assertNotNull(result);
        assertEquals("Alice", result.name());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testGetUserById_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserById("999"));
    }

    @Test
    void testCreateUser_newUserWithValidData_createsAndReturnsUser() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("alice", "Alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            return new UserEntity("generated-id-123", entity.getUsername(), entity.getName(), entity.getEmail(), entity.getPassword(), entity.getRoles());
        });

        // Act
        UserResponse result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("Alice", result.name());
        assertEquals("alice@example.com", result.email());
        verify(userRepository, times(1)).save(any(UserEntity.class));
    }

    @Test
    void testChangeName_existingUserRequested_changesNameSuccessfully() throws EntityNotFoundException {
        // Arrange
        UserEntity existing = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponse result = userService.changeName("1", "Alicia");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("Alicia", result.name());
        assertEquals("alice@example.com", result.email());
    }

    @Test
    void testChangeName_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.changeName("999", "NewName"));
    }

    @Test
    void testDeleteUser_existingUserRequested_deletesSuccessfully() throws EntityNotFoundException {
        // Arrange
        UserEntity existing = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(existing));

        // Act
        userService.deleteUser("1");

        // Assert
        verify(userRepository, times(1)).deleteById("1");
    }

    @Test
    void testDeleteUser_nonExistingUserRequested_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.deleteUser("999"));
    }

    // --- getAllUsers edge cases ---

    @Test
    void testGetAllUsers_withNoUsers_returnsEmptyList() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<UserResponse> result = userService.getAllUsers();

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // --- getUserEntityById ---

    @Test
    void testGetUserEntityById_existingUser_returnsEntity() {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userService.getUserEntityById("1");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("alice", result.getUsername());
    }

    @Test
    void testGetUserEntityById_nonExistingUser_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserEntityById("999"));
    }

    // --- createUser edge cases ---

    @Test
    void testCreateUser_reservedAdminUsername_throwsIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("admin", "Admin", "admin@example.com", "password123");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(request));
        assertEquals("Username 'admin' is reserved.", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void testCreateUser_duplicateUsername_throwsIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("alice", "Alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(request));
        assertEquals("Username already exists: alice", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void testCreateUser_duplicateEmail_throwsIllegalArgumentException() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("bob", "Bob", "alice@example.com", "password123");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.createUser(request));
        assertEquals("Email already exists: alice@example.com", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void testCreateUser_validData_encodesPassword() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("alice", "Alice", "alice@example.com", "mypassword");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("mypassword")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("gen-id");
            return entity;
        });

        // Act
        UserResponse result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        verify(passwordEncoder, times(1)).encode("mypassword");
    }

    @Test
    void testCreateUser_validData_assignsUserRole() {
        // Arrange
        CreateUserRequest request = new CreateUserRequest("alice", "Alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("gen-id");
            return entity;
        });

        // Act
        UserResponse result = userService.createUser(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.roles().size());
        assertEquals("ROLE_USER", result.roles().get(0));
    }

    // --- changeName for admin user ---

    @Test
    void testChangeName_adminUser_throwsIllegalArgumentException() {
        // Arrange
        UserEntity adminUser = makeUser("1", "admin", "Admin", "admin@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(adminUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.changeName("1", "NewName"));
        assertEquals("The default administrator account cannot be altered.", exception.getMessage());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    // --- deleteUser for admin user ---

    @Test
    void testDeleteUser_adminUser_throwsIllegalArgumentException() {
        // Arrange
        UserEntity adminUser = makeUser("1", "admin", "Admin", "admin@example.com");
        when(userRepository.findById("1")).thenReturn(Optional.of(adminUser));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser("1"));
        assertEquals("The default administrator account cannot be altered.", exception.getMessage());
        verify(userRepository, never()).deleteById("1");
    }

    // --- getUserByEmail ---

    @Test
    void testGetUserByEmail_existingEmail_returnsUser() {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getUserByEmail("alice@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("alice@example.com", result.email());
        assertEquals("Alice", result.name());
    }

    @Test
    void testGetUserByEmail_nonExistingEmail_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserByEmail("unknown@example.com"));
    }

    // --- getUserEntityByEmail ---

    @Test
    void testGetUserEntityByEmail_existingEmail_returnsEntity() {
        // Arrange
        UserEntity user = makeUser("1", "alice", "Alice", "alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        // Act
        UserEntity result = userService.getUserEntityByEmail("alice@example.com");

        // Assert
        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("alice@example.com", result.getEmail());
    }

    @Test
    void testGetUserEntityByEmail_nonExistingEmail_throwsEntityNotFoundException() {
        // Arrange
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> userService.getUserEntityByEmail("unknown@example.com"));
    }

    // --- toResponse edge case: null roles ---

    @Test
    void testGetUserById_userWithNullRoles_returnsEmptyRolesList() {
        // Arrange
        UserEntity user = new UserEntity("1", "alice", "Alice", "alice@example.com", "hashed", null);
        when(userRepository.findById("1")).thenReturn(Optional.of(user));

        // Act
        UserResponse result = userService.getUserById("1");

        // Assert
        assertNotNull(result);
        assertTrue(result.roles().isEmpty());
    }
}
