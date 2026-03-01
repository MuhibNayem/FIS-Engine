package com.bracit.fisprocess.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityModeStartupIndicator Tests")
class SecurityModeStartupIndicatorTest {

    @Test
    @DisplayName("should publish warning marker metrics when security is disabled")
    void shouldPublishInsecureMarkersWhenSecurityDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        SecurityModeStartupIndicator indicator = new SecurityModeStartupIndicator(
                environment,
                providerWith(meterRegistry),
                false,
                true);

        indicator.report();

        assertThat(meterRegistry.get("fis.security.mode.startup")
                .tag("mode", "insecure")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.security.insecure.mode")
                .gauge()
                .value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should publish secure gauge value when security is enabled")
    void shouldPublishSecureGaugeWhenSecurityEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        SecurityModeStartupIndicator indicator = new SecurityModeStartupIndicator(
                environment,
                providerWith(meterRegistry),
                true,
                false);

        indicator.report();

        assertThat(meterRegistry.find("fis.security.mode.startup")
                .tag("mode", "insecure")
                .counter()).isNull();
        assertThat(meterRegistry.get("fis.security.insecure.mode")
                .gauge()
                .value()).isEqualTo(0.0);
    }

    private static ObjectProvider<MeterRegistry> providerWith(MeterRegistry meterRegistry) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("meterRegistry", meterRegistry);
        return beanFactory.getBeanProvider(MeterRegistry.class);
    }
}
