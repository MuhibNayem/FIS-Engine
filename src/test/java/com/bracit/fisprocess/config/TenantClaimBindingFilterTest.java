package com.bracit.fisprocess.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimBindingFilter Tests")
class TenantClaimBindingFilterTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("allows request when header tenant matches JWT tenant claim")
    void allowsWhenTenantMatchesClaim() throws Exception {
        TenantClaimBindingFilter filter = new TenantClaimBindingFilter(true, "tenant_id");
        MockHttpServletRequest request = requestWithTenant("d6b6ec39-dac3-4d6d-bf88-0af6d95045c8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        authenticateWithTenant("tenant_id", "d6b6ec39-dac3-4d6d-bf88-0af6d95045c8");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.invoked).isTrue();
    }

    @Test
    @DisplayName("rejects request when header tenant does not match JWT tenant claim")
    void rejectsOnTenantMismatch() throws Exception {
        TenantClaimBindingFilter filter = new TenantClaimBindingFilter(true, "tenant_id");
        MockHttpServletRequest request = requestWithTenant("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        authenticateWithTenant("tenant_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("does not match JWT tenant context");
        assertThat(chain.invoked).isFalse();
    }

    @Test
    @DisplayName("rejects request when required tenant claim is missing")
    void rejectsWhenTenantClaimMissing() throws Exception {
        TenantClaimBindingFilter filter = new TenantClaimBindingFilter(true, "tenant_id");
        MockHttpServletRequest request = requestWithTenant("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        authenticateWithTenant("other_claim", "x");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("missing required tenant claim");
        assertThat(chain.invoked).isFalse();
    }

    @Test
    @DisplayName("bypasses claim validation when enforcement is disabled")
    void bypassesWhenEnforcementDisabled() throws Exception {
        TenantClaimBindingFilter filter = new TenantClaimBindingFilter(false, "tenant_id");
        MockHttpServletRequest request = requestWithTenant("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();
        authenticateWithTenant("tenant_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.invoked).isTrue();
    }

    private static void authenticateWithTenant(String claimName, String claimValue) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(claimName, claimValue));
        var authentication = new UsernamePasswordAuthenticationToken(jwt, jwt, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static MockHttpServletRequest requestWithTenant(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/accounts");
        request.addHeader("X-Tenant-Id", tenantId);
        return request;
    }

    private static final class CapturingFilterChain implements FilterChain {
        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException, ServletException {
            invoked = true;
        }
    }
}
