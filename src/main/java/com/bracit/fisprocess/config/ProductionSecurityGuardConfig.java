package com.bracit.fisprocess.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fail-fast guard to prevent production startup with disabled API security.
 */
@Configuration
@Profile("prod")
public class ProductionSecurityGuardConfig {

    @Bean
    InitializingBean productionSecurityEnabledGuard(
            @Value("${fis.security.enabled:true}") boolean securityEnabled) {
        return () -> {
            if (!securityEnabled) {
                throw new IllegalStateException(
                        "Invalid configuration: fis.security.enabled=false is forbidden in prod profile.");
            }
        };
    }
}
