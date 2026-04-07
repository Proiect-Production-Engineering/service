package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.prodeng.model.RoleEntity;
import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplementationTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImplementation userDetailsService;

    // --- loadUserByUsername ---

    @Test
    void testLoadUserByUsername_existingUser_returnsUserDetails() {
        // Arrange
        UserEntity user = new UserEntity("1", "alice", "Alice", "alice@example.com",
                "hashed", new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // Act
        UserDetails result = userDetailsService.loadUserByUsername("alice");

        // Assert
        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals("hashed", result.getPassword());
        assertEquals(1, result.getAuthorities().size());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        verify(userRepository, times(1)).findByUsername("alice");
    }

    @Test
    void testLoadUserByUsername_nonExistingUser_throwsUsernameNotFoundException() {
        // Arrange
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
        assertEquals("User not found with username: unknown", exception.getMessage());
        verify(userRepository, times(1)).findByUsername("unknown");
    }

    @Test
    void testLoadUserByUsername_userWithMultipleRoles_returnsAllAuthorities() {
        // Arrange
        UserEntity user = new UserEntity("1", "admin", "Admin", "admin@example.com",
                "hashed", new ArrayList<>(List.of(
                        new RoleEntity("ROLE_USER"),
                        new RoleEntity("ROLE_ADMIN")
                )));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        // Act
        UserDetails result = userDetailsService.loadUserByUsername("admin");

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getAuthorities().size());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testLoadUserByUsername_userWithNoRoles_returnsEmptyAuthorities() {
        // Arrange
        UserEntity user = new UserEntity("1", "norole", "NoRole", "norole@example.com",
                "hashed", null);
        when(userRepository.findByUsername("norole")).thenReturn(Optional.of(user));

        // Act
        UserDetails result = userDetailsService.loadUserByUsername("norole");

        // Assert
        assertNotNull(result);
        assertEquals("norole", result.getUsername());
        assertTrue(result.getAuthorities().isEmpty());
    }

    @Test
    void testLoadUserByUsername_userWithEmptyRoles_returnsEmptyAuthorities() {
        // Arrange
        UserEntity user = new UserEntity("1", "emptyrole", "EmptyRole", "empty@example.com",
                "hashed", Collections.emptyList());
        when(userRepository.findByUsername("emptyrole")).thenReturn(Optional.of(user));

        // Act
        UserDetails result = userDetailsService.loadUserByUsername("emptyrole");

        // Assert
        assertNotNull(result);
        assertTrue(result.getAuthorities().isEmpty());
    }

    @Test
    void testLoadUserByUsername_existingUser_accountIsEnabled() {
        // Arrange
        UserEntity user = new UserEntity("1", "alice", "Alice", "alice@example.com",
                "hashed", new ArrayList<>(List.of(new RoleEntity("ROLE_USER"))));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // Act
        UserDetails result = userDetailsService.loadUserByUsername("alice");

        // Assert
        assertTrue(result.isEnabled());
        assertTrue(result.isAccountNonExpired());
        assertTrue(result.isAccountNonLocked());
        assertTrue(result.isCredentialsNonExpired());
    }
}
