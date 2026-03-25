package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.request.SignInRequest;
import ro.unibuc.prodeng.request.SignUpRequest;
import ro.unibuc.prodeng.service.AuthenticationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
class AuthenticationControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private AuthenticationController authenticationController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authenticationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- POST /api/auth/signin ---

    @Test
    void testSignIn_validCredentials_returnsOkWithToken() throws Exception {
        // Arrange
        SignInRequest request = new SignInRequest("alice", "password123");
        when(authenticationService.signInUser(any(SignInRequest.class))).thenReturn("jwt-token-123");

        // Act & Assert
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt-token-123"));

        verify(authenticationService, times(1)).signInUser(any(SignInRequest.class));
    }

    @Test
    void testSignIn_invalidCredentials_serviceThrowsException() throws Exception {
        // Arrange
        SignInRequest request = new SignInRequest("alice", "wrong");
        when(authenticationService.signInUser(any(SignInRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    // --- POST /api/auth/signup ---

    @Test
    void testSignUp_validRequest_returnsOkWithToken() throws Exception {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(authenticationService.signUpUser(any(SignUpRequest.class))).thenReturn("jwt-token-456");

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt-token-456"));

        verify(authenticationService, times(1)).signUpUser(any(SignUpRequest.class));
    }

    @Test
    void testSignUp_duplicateUsername_returnsBadRequest() throws Exception {
        // Arrange
        SignUpRequest request = new SignUpRequest("alice", "alice@example.com", "password123");
        when(authenticationService.signUpUser(any(SignUpRequest.class)))
                .thenThrow(new IllegalArgumentException("Username already exists: alice"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists: alice"));
    }

    @Test
    void testSignUp_duplicateEmail_returnsBadRequest() throws Exception {
        // Arrange
        SignUpRequest request = new SignUpRequest("bob", "alice@example.com", "password123");
        when(authenticationService.signUpUser(any(SignUpRequest.class)))
                .thenThrow(new IllegalArgumentException("Email already exists: alice@example.com"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already exists: alice@example.com"));
    }

    @Test
    void testSignUp_reservedAdminUsername_returnsBadRequest() throws Exception {
        // Arrange
        SignUpRequest request = new SignUpRequest("admin", "admin@example.com", "password123");
        when(authenticationService.signUpUser(any(SignUpRequest.class)))
                .thenThrow(new IllegalArgumentException("Username 'admin' is reserved."));

        // Act & Assert
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username 'admin' is reserved."));
    }
}
