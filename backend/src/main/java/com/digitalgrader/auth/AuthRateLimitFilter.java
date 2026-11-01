package com.digitalgrader.auth;

import com.digitalgrader.common.ApiErrors;
import com.digitalgrader.common.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Throttles credential and registration endpoints per client IP. Rejects excess requests with 429 and Retry-After. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    private final Map<String, RateLimiter> limiters;

    public AuthRateLimitFilter(ObjectMapper mapper, Map<String, RateLimiter> limiters) {
        this.mapper = mapper;
        this.limiters = limiters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter limiter = limiters.get(request.getRequestURI());
        if (limiter == null) { chain.doFilter(request, response); return; }
        if (limiter.allow(clientIp(request))) { chain.doFilter(request, response); return; }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(limiter.retryAfterSeconds()));
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), ApiErrors.body(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, retry later"));
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
        return request.getRemoteAddr();
    }
}
