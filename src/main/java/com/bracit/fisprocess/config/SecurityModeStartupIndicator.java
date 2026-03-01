package com.bracit.fisprocess.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Emits explicit startup markers for the active security mode.
 */
@Component
@Slf4j
public class SecurityModeStartupIndicator {

    private final Environment environment;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final boolean securityEnabled;
    private final boolean allowInsecureMode;
    private final AtomicInteger insecureModeGauge = new AtomicInteger(0);

    public SecurityModeStartupIndicator(
            Environment environment,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${fis.security.enabled:true}") boolean securityEnabled,
            @Value("${fis.security.allow-insecure-mode:false}") boolean allowInsecureMode) {
        this.environment = environment;
        this.meterRegistryProvider = meterRegistryProvider;
        this.securityEnabled = securityEnabled;
        this.allowInsecureMode = allowInsecureMode;
    }

    @PostConstruct
    void report() {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            meterRegistry.gauge("fis.security.insecure.mode", insecureModeGauge);
        }

        if (securityEnabled) {
            insecureModeGauge.set(0);
            return;
        }

        insecureModeGauge.set(1);
        String profiles = Arrays.toString(environment.getActiveProfiles());
        log.warn("""
                ============================================================
                SECURITY WARNING: API security is DISABLED.
                fis.security.enabled=false
                fis.security.allow-insecure-mode={}
                activeProfiles={}
                SECURITY_MODE=INSECURE
                This mode is for local/dev/test only.
                ============================================================
                """, allowInsecureMode, profiles);

        if (meterRegistry != null) {
            meterRegistry.counter("fis.security.mode.startup", "mode", "insecure").increment();
        }
    }
}
