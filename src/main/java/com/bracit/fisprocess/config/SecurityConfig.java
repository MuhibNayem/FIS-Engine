package com.bracit.fisprocess.config;

import com.bracit.fisprocess.repository.BusinessEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final BusinessEntityRepository businessEntityRepository;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${fis.security.enabled:true}") boolean securityEnabled) throws Exception {
        if (!securityEnabled) {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/openapi.yaml", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/**").hasAnyRole("FIS_READER", "FIS_ACCOUNTANT", "FIS_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/events", "/v1/journal-entries", "/v1/journal-entries/*/reverse",
                                "/v1/journal-entries/*/correct")
                        .hasAnyRole("FIS_ACCOUNTANT", "FIS_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/revaluations/**", "/v1/mapping-rules", "/v1/accounting-periods",
                                "/v1/exchange-rates", "/v1/accounts")
                        .hasRole("FIS_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/v1/mapping-rules/**").hasRole("FIS_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/v1/accounting-periods/**", "/v1/accounts/**").hasRole("FIS_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/v1/mapping-rules/**").hasRole("FIS_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(new TenantValidationFilter(businessEntityRepository), UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${fis.security.enabled:true}") boolean securityEnabled,
            @Value("${fis.security.jwt.public-key-pem:}") String publicKeyPem) {
        if (!securityEnabled) {
            return token -> {
                throw new JwtException("JWT decoding is disabled because security is disabled.");
            };
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException("fis.security.jwt.public-key-pem must be configured.");
        }
        PublicKey publicKey = parsePublicKey(publicKeyPem);
        if (publicKey instanceof RSAPublicKey rsaPublicKey) {
            return NimbusJwtDecoder.withPublicKey(rsaPublicKey).build();
        }
        throw new IllegalStateException("Unsupported JWT public key type: " + publicKey.getAlgorithm()
                + ". Configure an RSA public key.");
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    private PublicKey parsePublicKey(String pem) {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(normalized);
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key configuration", ex);
        }
    }
}
