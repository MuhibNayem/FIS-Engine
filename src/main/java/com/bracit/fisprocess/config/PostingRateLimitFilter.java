package com.bracit.fisprocess.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Redis-backed distributed rate limiter for high-risk posting endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
@Slf4j
public class PostingRateLimitFilter extends OncePerRequestFilter {

    private static final DefaultRedisScript<Long> RATE_LIMIT_INCR_SCRIPT = new DefaultRedisScript<>(
            """
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
                    end
                    return current
                    """,
            Long.class);

    private final StringRedisTemplate redisTemplate;

    @Value("${fis.rate-limit.enabled:false}")
    private boolean enabled;

    @Value("${fis.rate-limit.window-seconds:1}")
    private long windowSeconds;

    @Value("${fis.rate-limit.requests-per-window:60}")
    private int requestsPerWindow;

    @Value("${fis.rate-limit.fail-open:true}")
    private boolean failOpen;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !isTargetedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isRateLimited(request)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.getWriter().write("""
                    {"type":"/problems/rate-limit-exceeded","title":"Too Many Requests","status":429,
                    "detail":"Rate limit exceeded for posting endpoints.","instance":"%s","timestamp":"%s"}
                    """.formatted(request.getRequestURI(), OffsetDateTime.now()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTargetedPath(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.startsWith("/v1/events")
                || path.startsWith("/v1/journal-entries")
                || path.startsWith("/v1/settlements")
                || path.startsWith("/v1/revaluations");
    }

    private String rateLimitKey(HttpServletRequest request) {
        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null && !tenantId.isBlank()) {
            return "tenant:" + tenantId;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private boolean isRateLimited(HttpServletRequest request) {
        long nowEpochSeconds = System.currentTimeMillis() / 1000;
        long windowStart = nowEpochSeconds - (nowEpochSeconds % windowSeconds);
        String key = "fis:rate-limit:" + rateLimitKey(request) + ":" + windowStart;

        try {
            Long currentCount = redisTemplate.execute(
                    RATE_LIMIT_INCR_SCRIPT,
                    List.of(key),
                    String.valueOf(windowSeconds));
            if (currentCount == null) {
                log.warn("Redis rate limit script returned null for key='{}'; failOpen={}", key, failOpen);
                return !failOpen;
            }
            return currentCount > requestsPerWindow;
        } catch (RuntimeException ex) {
            log.warn("Redis rate limiter unavailable for key='{}'; failOpen={}", key, failOpen, ex);
            return !failOpen;
        }
    }
}
