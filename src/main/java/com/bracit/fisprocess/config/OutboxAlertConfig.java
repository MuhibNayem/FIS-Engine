package com.bracit.fisprocess.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Typed configuration properties for outbox alert thresholds.
 * <p>
 * Bound to {@code fis.outbox.*} in {@code application.yml}.
 * Centralises all alerting constants so operators can tune them
 * without code changes.
 */
@Configuration
@ConfigurationProperties(prefix = "fis.outbox")
@Getter
@Setter
public class OutboxAlertConfig {

    /**
     * Maximum retry attempts per event before automatic DLQ.
     */
    private int maxRetries = 50;

    private Alert alert = new Alert();

    @Getter
    @Setter
    public static class Alert {

        /**
         * Number of unpublished events that triggers a WARNING.
         */
        private int backlogWarning = 1000;

        /**
         * Age in seconds of the oldest unpublished event that triggers a WARNING.
         */
        private int oldestWarningSeconds = 300;

        /**
         * DLQ size that triggers a CRITICAL alert.
         */
        private int dlqCritical = 100;

        /**
         * Consecutive retry failures (global streak) that triggers a WARNING.
         */
        private int retryStreakWarning = 10;
    }
}
