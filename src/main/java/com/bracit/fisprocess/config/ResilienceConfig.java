package com.bracit.fisprocess.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(
            MeterRegistry meterRegistry,
            @Value("${fis.resilience.circuit-breaker.sliding-window-size:20}") int slidingWindowSize,
            @Value("${fis.resilience.circuit-breaker.minimum-number-of-calls:10}") int minimumNumberOfCalls,
            @Value("${fis.resilience.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${fis.resilience.circuit-breaker.wait-duration-open-seconds:15}") long waitDurationOpenSeconds) {

        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitDurationOpenSeconds))
                .recordException(ex -> ex instanceof RuntimeException)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);
        registry.circuitBreaker("redisIdempotency");
        registry.circuitBreaker("rabbitOutboxPublish");
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    CircuitBreaker redisIdempotencyCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("redisIdempotency");
    }

    @Bean
    CircuitBreaker rabbitOutboxPublishCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("rabbitOutboxPublish");
    }
}
