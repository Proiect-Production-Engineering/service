package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import ro.unibuc.prodeng.model.UserEntity;
import ro.unibuc.prodeng.repository.UserRepository;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.security.jwt.JwtUtilities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class AuthenticationServiceImplementationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtilities jwtUtilities;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private AuthenticationServiceImplementation authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- signInUser ---

    @Test
    void testSignInUser_validCredentials_returnsJwtToken() {
        // Arrange
        SignInRequest request = new SignInRequest("alice", "password123");
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtUtilities.generateJwtToken(authentication)).thenReturn("jwt-token-123");

        // Act
        String token = authService.signInUser(request);

        // Assert
        assertEquals("jwt-token-123", token);
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtUtilities, times(1)).generateJwtToken(authentication);
    }

    @Test
    void testSignInUser_setsSecurityContext() {
        // Arrange
        SignInRequest request = new SignInRequest("alice", "password123");
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtilities.generateJwtToken(any())).thenReturn("token");

        // Act
        authService.signInUser(request);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // --- signUpUser ---

    @Test
    void testSignUpUser_validRequest_savesUserAndReturnsToken() {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("gen-id");
            return entity;
        });

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtilities.generateJwtToken(authentication)).thenReturn("jwt-token-456");

        // Act
        String token = authService.signUpUser(request);

        // Assert
        assertEquals("jwt-token-456", token);
        verify(userRepository, times(1)).save(any(UserEntity.class));
        verify(encoder, times(1)).encode("password123");
    }

    @Test
    void testSignUpUser_reservedAdminUsername_throwsIllegalArgumentException() {
        // Arrange
        SignUpRequest request = new SignUpRequest("admin", "admin@example.com", "password123");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.signUpUser(request));
        assertEquals("Username 'admin' is reserved.", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testSignUpUser_duplicateUsername_throwsIllegalArgumentException() {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.signUpUser(request));
        assertEquals("Username already exists: alice", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testSignUpUser_duplicateEmail_throwsIllegalArgumentException() {
        // Arrange
        SignUpRequest request = new SignUpRequest("bob", "alice@example.com", "password123");
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.signUpUser(request));
        assertEquals("Email already exists: alice@example.com", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testSignUpUser_assignsUserRole() {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("gen-id");
            return entity;
        });

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtilities.generateJwtToken(any())).thenReturn("token");

        // Act
        authService.signUpUser(request);

        // Assert — verify the entity saved has ROLE_USER
        verify(userRepository).save(argThat(user ->
                user.getRoles().size() == 1 &&
                user.getRoles().get(0).getName().equals("ROLE_USER")
        ));
    }

    @Test
    void testSignUpUser_afterSaving_callsSignInWithCorrectCredentials() {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId("gen-id");
            return entity;
        });

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtUtilities.generateJwtToken(authentication)).thenReturn("final-token");

        // Act
        String token = authService.signUpUser(request);

        // Assert
        assertEquals("final-token", token);
        verify(authenticationManager).authenticate(argThat(auth -> {
            UsernamePasswordAuthenticationToken usernameAuth = (UsernamePasswordAuthenticationToken) auth;
            return "alice".equals(usernameAuth.getPrincipal()) && "password123".equals(usernameAuth.getCredentials());
        }));
    }
}
