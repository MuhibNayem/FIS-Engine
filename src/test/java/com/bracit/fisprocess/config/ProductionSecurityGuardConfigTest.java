package com.bracit.fisprocess.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductionSecurityGuardConfig Tests")
class ProductionSecurityGuardConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionSecurityGuardConfig.class);

    @Test
    @DisplayName("should fail startup in prod when security is disabled")
    void shouldFailWhenSecurityDisabledInProd() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "fis.security.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining(
                            "fis.security.enabled=false is forbidden in prod profile");
                });
    }

    @Test
    @DisplayName("should start in prod when security is enabled")
    void shouldStartWhenSecurityEnabledInProd() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "fis.security.enabled=true")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("should allow security disabled outside prod")
    void shouldAllowSecurityDisabledOutsideProd() {
        contextRunner
                .withPropertyValues("fis.security.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
