package ro.unibuc.prodeng.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationEntryPointJwtTest {

    private final AuthenticationEntryPointJwt entryPoint = new AuthenticationEntryPointJwt();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- commence: response status ---

    @Test
    void testCommence_unauthorizedRequest_returns401Status() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Bad credentials");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        assertEquals(401, response.getStatus());
    }

    // --- commence: content type ---

    @Test
    void testCommence_unauthorizedRequest_returnsJsonContentType() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Bad credentials");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
    }

    // --- commence: response body structure ---

    @Test
    @SuppressWarnings("unchecked")
    void testCommence_unauthorizedRequest_returnsCorrectBodyStructure() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Invalid token");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(401, body.get("status"));
        assertEquals("Unauthorized", body.get("error"));
        assertEquals("Invalid token", body.get("message"));
        assertEquals("/api/users", body.get("path"));
    }

    // --- commence: message from exception ---

    @Test
    @SuppressWarnings("unchecked")
    void testCommence_withSpecificMessage_returnsExceptionMessage() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Full authentication is required");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("Full authentication is required", body.get("message"));
    }

    // --- commence: path from request ---

    @Test
    @SuppressWarnings("unchecked")
    void testCommence_differentPaths_returnsCorrectPath() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("Access denied");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals("/api/admin/settings", body.get("path"));
    }

    // --- commence: body has exactly 4 fields ---

    @Test
    @SuppressWarnings("unchecked")
    void testCommence_unauthorizedRequest_bodyContainsExactlyFourFields() throws IOException, ServletException {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("test");

        // Act
        entryPoint.commence(request, response, authException);

        // Assert
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertEquals(4, body.size());
        assertTrue(body.containsKey("status"));
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("message"));
        assertTrue(body.containsKey("path"));
    }
}
