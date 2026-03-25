package ro.unibuc.prodeng.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import ro.unibuc.prodeng.exception.EntityNotFoundException;
import ro.unibuc.prodeng.exception.GlobalExceptionHandler;
import ro.unibuc.prodeng.request.ChangeNameRequest;
import ro.unibuc.prodeng.request.CreateUserRequest;
import ro.unibuc.prodeng.response.UserResponse;
import ro.unibuc.prodeng.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UserResponse testUser1 = new UserResponse("1", "john", "John Doe", "john@example.com", List.of("ROLE_USER"));
    private final UserResponse testUser2 = new UserResponse("2", "jane", "Jane Smith", "jane@example.com", List.of("ROLE_USER"));
    private final CreateUserRequest createUserRequest = new CreateUserRequest("john", "John Doe", "john@example.com", "password123");
    private final ChangeNameRequest changeNameRequest = new ChangeNameRequest("John Updated");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testGetAllUsers_withMultipleUsers_returnsListOfUsers() throws Exception {
        // Arrange
        List<UserResponse> users = Arrays.asList(testUser1, testUser2);
        when(userService.getAllUsers()).thenReturn(users);

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("1")))
                .andExpect(jsonPath("$[0].name", is("John Doe")))
                .andExpect(jsonPath("$[0].email", is("john@example.com")))
                .andExpect(jsonPath("$[1].id", is("2")))
                .andExpect(jsonPath("$[1].name", is("Jane Smith")))
                .andExpect(jsonPath("$[1].email", is("jane@example.com")));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void testGetAllUsers_withNoUsers_returnsEmptyList() throws Exception {
        // Arrange
        when(userService.getAllUsers()).thenReturn(List.of());

        // Act & Assert
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    void testGetUserById_existingUserRequested_returnsUser() throws Exception {
        // Arrange
        String userId = "1";
        when(userService.getUserById(userId)).thenReturn(testUser1);

        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void testGetUserById_nonExistingUserRequested_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        when(userService.getUserById(userId)).thenThrow(new EntityNotFoundException("User"));

        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void testCreateUser_validRequestProvided_createsAndReturnsUser() throws Exception {
        // Arrange
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(testUser1);

        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).createUser(any(CreateUserRequest.class));
    }

    @Test
    void testUpdateUser_existingUserRequested_updatesAndReturnsUser() throws Exception {
        // Arrange
        String userId = "1";
        UserResponse updatedUser = new UserResponse("1", "john", "John Updated", "john@example.com", List.of("ROLE_USER"));
        when(userService.changeName(eq(userId), eq("John Updated"))).thenReturn(updatedUser);

        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeNameRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Updated")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).changeName(userId, "John Updated");
    }

    @Test
    void testUpdateUser_nonExistingUserRequested_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        when(userService.changeName(eq(userId), anyString()))
                .thenThrow(new EntityNotFoundException("User"));

        // Act & Assert
        mockMvc.perform(put("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeNameRequest)))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).changeName(eq(userId), anyString());
    }

    // --- GET /api/users/me ---

    @Test
    void testGetCurrentUser_authenticatedUser_returnsCurrentUser() throws Exception {
        // Arrange
        when(userService.getCurrentUser()).thenReturn(testUser1);

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.username", is("john")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_USER")));

        verify(userService, times(1)).getCurrentUser();
    }

    @Test
    void testGetCurrentUser_userNotFound_returnsNotFound() throws Exception {
        // Arrange
        when(userService.getCurrentUser()).thenThrow(new EntityNotFoundException("User"));

        // Act & Assert
        mockMvc.perform(get("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).getCurrentUser();
    }

    // --- DELETE /api/users/{id} ---

    @Test
    void testDeleteUser_existingUser_returnsNoContent() throws Exception {
        // Arrange
        String userId = "1";
        doNothing().when(userService).deleteUser(userId);

        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(userService, times(1)).deleteUser(userId);
    }

    @Test
    void testDeleteUser_nonExistingUser_returnsNotFound() throws Exception {
        // Arrange
        String userId = "999";
        doThrow(new EntityNotFoundException("User")).when(userService).deleteUser(userId);

        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).deleteUser(userId);
    }

    @Test
    void testDeleteUser_adminUser_returnsBadRequest() throws Exception {
        // Arrange
        String userId = "1";
        doThrow(new IllegalArgumentException("The default administrator account cannot be altered."))
                .when(userService).deleteUser(userId);

        // Act & Assert
        mockMvc.perform(delete("/api/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("The default administrator account cannot be altered.")));
    }

    // --- GET /api/users/by-email ---

    @Test
    void testGetUserByEmail_existingEmail_returnsUser() throws Exception {
        // Arrange
        when(userService.getUserByEmail("john@example.com")).thenReturn(testUser1);

        // Act & Assert
        mockMvc.perform(get("/api/users/by-email")
                .param("email", "john@example.com")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")));

        verify(userService, times(1)).getUserByEmail("john@example.com");
    }

    @Test
    void testGetUserByEmail_nonExistingEmail_returnsNotFound() throws Exception {
        // Arrange
        when(userService.getUserByEmail("unknown@example.com"))
                .thenThrow(new EntityNotFoundException("User"));

        // Act & Assert
        mockMvc.perform(get("/api/users/by-email")
                .param("email", "unknown@example.com")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).getUserByEmail("unknown@example.com");
    }

    // --- POST /api/users with invalid data ---

    @Test
    void testCreateUser_duplicateUsername_returnsBadRequest() throws Exception {
        // Arrange
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Username already exists: john"));

        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Username already exists: john")));
    }

    @Test
    void testCreateUser_duplicateEmail_returnsBadRequest() throws Exception {
        // Arrange
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Email already exists: john@example.com"));

        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Email already exists: john@example.com")));
    }

    @Test
    void testCreateUser_reservedAdminUsername_returnsBadRequest() throws Exception {
        // Arrange
        CreateUserRequest adminRequest = new CreateUserRequest("admin", "Admin", "admin@example.com", "password123");
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(new IllegalArgumentException("Username 'admin' is reserved."));

        // Act & Assert
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Username 'admin' is reserved.")));
    }

    // --- GET /api/users/{id} edge case: verify response fields ---

    @Test
    void testGetUserById_existingUser_returnsAllFields() throws Exception {
        // Arrange
        UserResponse userWithRoles = new UserResponse("1", "john", "John Doe", "john@example.com", List.of("ROLE_USER", "ROLE_ADMIN"));
        when(userService.getUserById("1")).thenReturn(userWithRoles);

        // Act & Assert
        mockMvc.perform(get("/api/users/{id}", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.username", is("john")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.roles", hasSize(2)))
                .andExpect(jsonPath("$.roles[0]", is("ROLE_USER")))
                .andExpect(jsonPath("$.roles[1]", is("ROLE_ADMIN")));
    }
}
