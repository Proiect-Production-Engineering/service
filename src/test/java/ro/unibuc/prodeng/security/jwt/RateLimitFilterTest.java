package ro.unibuc.prodeng.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class RateLimitFilterTest {

    @Mock
    private FilterChain filterChain;

    private RateLimitFilter rateLimitFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    // --- Non-auth endpoints are not rate limited ---

    @Test
    void testNonAuthEndpoint_alwaysPassesThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/accounts/me");
        request.setRemoteAddr("192.168.1.1");

        // Act — call many times, should never be blocked
        for (int i = 0; i < 50; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // Assert
        assertEquals(200, response.getStatus());
        verify(filterChain, times(50)).doFilter(any(), any());
    }

    // --- Auth endpoints are rate limited ---

    @Test
    void testAuthEndpoint_underLimit_passesThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("10.0.0.1");

        // Act
        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Assert
        assertEquals(200, response.getStatus());
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testAuthEndpoint_atExactLimit_passesThrough() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signup");
        request.setRemoteAddr("10.0.0.2");

        // Act — send exactly 20 requests (the limit)
        for (int i = 0; i < 20; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // Assert — all 20 should pass
        assertEquals(200, response.getStatus());
        verify(filterChain, times(20)).doFilter(any(), any());
    }

    @Test
    void testAuthEndpoint_exceedsLimit_returns429() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("10.0.0.3");

        // Act — send 21 requests (one over limit)
        for (int i = 0; i < 21; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // Assert — last request should be rate limited
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        assertTrue(response.getContentAsString().contains("Too many requests"));
        // Only 20 should have passed through to the filter chain
        verify(filterChain, times(20)).doFilter(any(), any());
    }

    @Test
    void testAuthEndpoint_multipleRequestsAfterLimit_allBlocked() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("10.0.0.4");

        // Act — send 25 requests
        int blockedCount = 0;
        for (int i = 0; i < 25; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
            if (response.getStatus() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                blockedCount++;
            }
        }

        // Assert — 5 should be blocked (requests 21-25)
        assertEquals(5, blockedCount);
        verify(filterChain, times(20)).doFilter(any(), any());
    }

    // --- Different IPs have separate limits ---

    @Test
    void testDifferentIps_haveSeparateLimits() throws ServletException, IOException {
        // Arrange — exhaust limit for IP1
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("1.1.1.1");

        for (int i = 0; i < 21; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());

        // Act — make request from a different IP
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/auth/signin");
        request2.setRemoteAddr("2.2.2.2");
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request2, response2, filterChain);

        // Assert — different IP should not be blocked
        assertEquals(200, response2.getStatus());
    }

    // --- Rate limiting is based on remoteAddr only; X-Forwarded-For is ignored ---

    @Test
    void testXForwardedForHeader_isIgnored_remoteAddrUsedForRateLimit() throws ServletException, IOException {
        // Arrange — same remoteAddr, different X-Forwarded-For headers
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("127.0.0.1");

        // Exhaust limit using remoteAddr "127.0.0.1"
        for (int i = 0; i < 21; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI("/api/auth/signin");
            req.setRemoteAddr("127.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113." + i); // different each time — must be ignored
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(req, response, filterChain);
        }

        // Assert — rate limited by remoteAddr, not by the varying X-Forwarded-For values
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());

        // A request from a genuinely different remoteAddr should still pass
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/auth/signin");
        request2.setRemoteAddr("10.10.10.10");
        request2.addHeader("X-Forwarded-For", "127.0.0.1"); // spoofed — must be ignored
        MockHttpServletResponse response2 = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request2, response2, filterChain);
        assertEquals(200, response2.getStatus());
    }

    // --- Response body format ---

    @Test
    void testRateLimited_responseBodyIsValidJson() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signin");
        request.setRemoteAddr("10.0.0.99");

        // Exhaust limit
        for (int i = 0; i < 21; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // Assert
        String body = response.getContentAsString();
        assertEquals("{\"error\":\"Too many requests. Please try again later.\"}", body);
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.getContentType());
        assertEquals(429, response.getStatus());
    }

    // --- Signup endpoint is also rate limited ---

    @Test
    void testSignupEndpoint_alsoRateLimited() throws ServletException, IOException {
        // Arrange
        request.setRequestURI("/api/auth/signup");
        request.setRemoteAddr("10.0.0.50");

        // Act — exhaust limit
        for (int i = 0; i < 21; i++) {
            response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(request, response, filterChain);
        }

        // Assert
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
        verify(filterChain, times(20)).doFilter(any(), any());
    }
}
