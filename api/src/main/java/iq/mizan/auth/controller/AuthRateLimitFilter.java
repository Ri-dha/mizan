package iq.mizan.auth.controller;

import java.io.IOException;
import java.time.Duration;

import iq.mizan.auth.AuthProperties;
import iq.mizan.common.exception.ApiProblemProperties;
import iq.mizan.common.exception.ErrorCode;
import iq.mizan.common.web.CorrelationIdFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Per-address token bucket on the unauthenticated auth endpoints (NFR-04). */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(10);

    private final AuthProperties.RateLimit limit;
    private final ApiProblemProperties problemProperties;
    private final JsonMapper jsonMapper;
    private final Cache<String, Bucket> buckets;

    public AuthRateLimitFilter(
            AuthProperties authProperties, ApiProblemProperties problemProperties, JsonMapper jsonMapper) {
        this.limit = authProperties.rateLimit();
        this.problemProperties = problemProperties;
        this.jsonMapper = jsonMapper;
        this.buckets = Caffeine.newBuilder().expireAfterAccess(IDLE_EVICTION).build();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AUTH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        Bucket bucket = buckets.get(ClientRequest.ip(request), ip -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.status().value());
        response.setHeader("Retry-After", String.valueOf(limit.refillPeriod().toSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(jsonMapper.writeValueAsString(java.util.Map.of(
                "type", problemProperties.typeFor(ErrorCode.RATE_LIMIT_EXCEEDED),
                "title", ErrorCode.RATE_LIMIT_EXCEEDED.title(),
                "status", ErrorCode.RATE_LIMIT_EXCEEDED.status().value(),
                "detail", "Too many attempts. Please wait and try again.",
                "code", ErrorCode.RATE_LIMIT_EXCEEDED.code(),
                "correlationId", String.valueOf(CorrelationIdFilter.current()))));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.refillPeriod())
                        .build())
                .build();
    }
}
