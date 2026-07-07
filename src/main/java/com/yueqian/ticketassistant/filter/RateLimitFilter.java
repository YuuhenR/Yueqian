package com.yueqian.ticketassistant.filter;

import com.yueqian.ticketassistant.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (StringUtils.hasText(securityProperties.getApiKey())) {
            String apiKey = request.getHeader("X-API-Key");
            if (!securityProperties.getApiKey().equals(apiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\":false,\"message\":\"API Key invalid\"}");
                return;
            }
        }
        String key = clientIp(request);
        Window window = windows.compute(key, (k, old) -> old == null || old.expired() ? new Window() : old);
        if (window.count.incrementAndGet() > securityProperties.getMaxRequestsPerMinute()) {
            response.setStatus(429);
            response.getWriter().write("{\"success\":false,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class Window {
        private final long start = Instant.now().getEpochSecond();
        private final AtomicInteger count = new AtomicInteger();

        private boolean expired() {
            return Instant.now().getEpochSecond() - start >= 60;
        }
    }
}
