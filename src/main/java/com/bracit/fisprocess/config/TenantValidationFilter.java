package com.bracit.fisprocess.config;

import com.bracit.fisprocess.repository.BusinessEntityRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enforces valid and active tenant context on all business API calls.
 */
@RequiredArgsConstructor
public class TenantValidationFilter extends OncePerRequestFilter {

    private final BusinessEntityRepository businessEntityRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantIdHeader = request.getHeader("X-Tenant-Id");
        if (tenantIdHeader == null || tenantIdHeader.isBlank()) {
            writeValidationProblem(response, "Required header 'X-Tenant-Id' is missing.");
            return;
        }

        final UUID tenantId;
        try {
            tenantId = UUID.fromString(tenantIdHeader);
        } catch (IllegalArgumentException ex) {
            writeValidationProblem(response, "Header 'X-Tenant-Id' must be a valid UUID.");
            return;
        }

        if (businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId).isEmpty()) {
            writeValidationProblem(response, "Tenant is not active or does not exist.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeValidationProblem(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"/problems/validation-failed\",\"title\":\"Validation Failed\",\"status\":400,\"detail\":\""
                + detail.replace("\"", "\\\"") + "\"}");
    }
}
