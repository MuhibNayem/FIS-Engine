package com.bracit.fisprocess.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.time.Duration;

@Component
@Slf4j
public class ResponseCachingInterceptor implements HandlerInterceptor {

    @Value("${fis.cache.response.enabled:true}")
    private boolean responseCachingEnabled;

    @Value("${fis.cache.response.default-ttl-seconds:30}")
    private int defaultTtlSeconds;

    @Value("${fis.cache.response.accounting-periods-ttl-seconds:300}")
    private int accountingPeriodsTtlSeconds;

    @Value("${fis.cache.response.accounts-ttl-seconds:300}")
    private int accountsTtlSeconds;

    @Value("${fis.cache.response.reports-ttl-seconds:10}")
    private int reportsTtlSeconds;

    private static final String CACHE_HEADER = "Cache-Control";
    private static final String ETAG_HEADER = "ETag";
    private static final String ETAG_PREFIX = "\"";
    private static final String ETAG_SUFFIX = "\"";

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) {
        if (!responseCachingEnabled) {
            return;
        }

        String path = request.getRequestURI();
        int ttl = determineTtl(path);

        if (ttl > 0) {
            CacheControl cacheControl = CacheControl.maxAge(Duration.ofSeconds(ttl))
                    .cachePublic()
                    .noTransform();

            response.setHeader(CACHE_HEADER, cacheControl.getHeaderValue());

            String etag = generateETag(request, path);
            response.setHeader(ETAG_HEADER, etag);

            log.debug("Set Cache-Control header for {}: {} (TTL: {}s)", path, cacheControl.getHeaderValue(), ttl);
        }
    }

    private int determineTtl(String path) {
        if (path.contains("/accounting-periods")) {
            return accountingPeriodsTtlSeconds;
        }
        if (path.contains("/accounts")) {
            return accountsTtlSeconds;
        }
        if (path.contains("/reports")) {
            return reportsTtlSeconds;
        }
        if (path.startsWith("/v1/") && "GET".equalsIgnoreCase("GET")) {
            return defaultTtlSeconds;
        }
        return 0;
    }

    private String generateETag(HttpServletRequest request, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod())
                .append("-")
                .append(path);

        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId != null) {
            sb.append("-").append(tenantId);
        }

        return ETAG_PREFIX + Integer.toHexString(sb.toString().hashCode()) + ETAG_SUFFIX;
    }
}