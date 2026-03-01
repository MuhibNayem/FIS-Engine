package com.bracit.fisprocess.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Ensures tenant header context is bound to the authenticated JWT tenant claim.
 */
@RequiredArgsConstructor
public class TenantClaimBindingFilter extends OncePerRequestFilter {

    private final boolean enforceTenantClaim;
    private final String tenantClaimName;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enforceTenantClaim) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            // TenantValidationFilter handles header required/format checks.
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        String claimTenantId = jwt.getClaimAsString(tenantClaimName);
        if (claimTenantId == null || claimTenantId.isBlank()) {
            writeTenantProblem(response, "JWT is missing required tenant claim '%s'.".formatted(tenantClaimName));
            return;
        }
        if (!tenantIdHeader.equals(claimTenantId)) {
            writeTenantProblem(response, "Header 'X-Tenant-Id' does not match JWT tenant context.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTenantProblem(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"/problems/tenant-context-mismatch\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\""
                + detail.replace("\"", "\\\"") + "\"}");
    }
}
