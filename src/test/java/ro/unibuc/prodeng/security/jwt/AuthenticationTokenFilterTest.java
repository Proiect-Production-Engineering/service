package ro.unibuc.prodeng.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.mockito.junit.jupiter.MockitoExtension;

import ro.unibuc.prodeng.model.UserDetails;
import ro.unibuc.prodeng.service.UserDetailsServiceImplementation;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationTokenFilterTest {

    @Mock
    private JwtUtilities jwtUtilities;

    @Mock
    private UserDetailsServiceImplementation userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private AuthenticationTokenFilter authenticationTokenFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Valid token flow ---

    @Test
    void testDoFilterInternal_validBearerToken_setsAuthentication() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtilities.validateJwtToken(token)).thenReturn(true);
        when(jwtUtilities.getUserNameFromJwtToken(token)).thenReturn("alice");

        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice", ((UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_validToken_setsCorrectAuthorities() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtilities.validateJwtToken(token)).thenReturn(true);
        when(jwtUtilities.getUserNameFromJwtToken(token)).thenReturn("admin");

        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("admin")
                .email("admin@example.com")
                .password("hashed")
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                ))
                .build();
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(2, auth.getAuthorities().size());
    }

    // --- No Authorization header ---

    @Test
    void testDoFilterInternal_noAuthorizationHeader_doesNotSetAuthentication() throws ServletException, IOException {
        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtilities, never()).validateJwtToken(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- Non-Bearer header ---

    @Test
    void testDoFilterInternal_nonBearerAuthorizationHeader_doesNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtilities, never()).validateJwtToken(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- Invalid token ---

    @Test
    void testDoFilterInternal_invalidToken_doesNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        String token = "invalid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtilities.validateJwtToken(token)).thenReturn(false);

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtilities, never()).getUserNameFromJwtToken(anyString());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- Exception during authentication ---

    @Test
    void testDoFilterInternal_exceptionDuringProcessing_continuesFilterChain() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtilities.validateJwtToken(token)).thenReturn(true);
        when(jwtUtilities.getUserNameFromJwtToken(token)).thenThrow(new RuntimeException("Unexpected error"));

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- Empty Bearer prefix ---

    @Test
    void testDoFilterInternal_emptyBearerToken_doesNotSetAuthentication() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Bearer ");

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    // --- Verify filter always calls chain ---

    @Test
    void testDoFilterInternal_validToken_alwaysCallsFilterChain() throws ServletException, IOException {
        // Arrange
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtUtilities.validateJwtToken(token)).thenReturn(true);
        when(jwtUtilities.getUserNameFromJwtToken(token)).thenReturn("alice");

        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilterInternal_invalidToken_alwaysCallsFilterChain() throws ServletException, IOException {
        // Arrange
        request.addHeader("Authorization", "Bearer bad-token");
        when(jwtUtilities.validateJwtToken("bad-token")).thenReturn(false);

        // Act
        authenticationTokenFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
    }
}
