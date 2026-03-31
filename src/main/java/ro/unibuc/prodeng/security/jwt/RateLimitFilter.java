package ro.unibuc.prodeng.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for authentication endpoints.
 * Limits requests per IP to prevent brute-force attacks.
 * <p>
 * Uses {@code request.getRemoteAddr()} for client IP resolution. When deployed
 * behind a reverse proxy, set {@code server.forward-headers-strategy=native} in
 * application.properties so that the container unwraps X-Forwarded-For before
 * the request reaches this filter.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int DEFAULT_MAX_REQUESTS = 20;
    private static final long WINDOW_MS = 60_000; // 1 minute

    private final int maxRequestsPerWindow;
    private final Map<String, RateWindow> requestCounts = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        this(DEFAULT_MAX_REQUESTS);
    }

    public RateLimitFilter(int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        // Evict expired windows to prevent unbounded memory growth
        requestCounts.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MS);

        RateWindow window = requestCounts.compute(clientIp, (key, existing) -> {
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new RateWindow(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > maxRequestsPerWindow) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static class RateWindow {
        final long windowStart;
        final AtomicInteger count;

        RateWindow(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
