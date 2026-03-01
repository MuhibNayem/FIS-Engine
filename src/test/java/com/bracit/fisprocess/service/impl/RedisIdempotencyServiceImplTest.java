package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import com.bracit.fisprocess.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisIdempotencyServiceImpl Unit Tests")
class RedisIdempotencyServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private IdempotencyLogRepository idempotencyLogRepository;
    @Mock
    private JsonMapper jsonMapper;

    private RedisIdempotencyServiceImpl service;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of(
                "redisIdempotencyTest",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .build());
        service = new RedisIdempotencyServiceImpl(
                redisTemplate,
                idempotencyLogRepository,
                jsonMapper,
                circuitBreaker);
    }

    @Test
    @DisplayName("checkAndMarkProcessing should use PostgreSQL fallback when Redis is unavailable")
    void checkAndMarkProcessing_shouldUsePostgresFallback_whenRedisUnavailable() {
        UUID tenantId = UUID.randomUUID();
        String eventId = "EVT-REDIS-DOWN-1";
        String payloadHash = "hash-a";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenThrow(new RuntimeException("redis down"));
        when(idempotencyLogRepository.findByTenantIdAndEventIdForUpdate(tenantId, eventId)).thenReturn(Optional.empty());
        when(idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)).thenReturn(Optional.empty());

        IdempotencyService.IdempotencyCheckResult result = service.checkAndMarkProcessing(tenantId, eventId, payloadHash);

        assertThat(result.state()).isEqualTo(IdempotencyService.IdempotencyState.NEW);

        ArgumentCaptor<IdempotencyLog> captor = ArgumentCaptor.forClass(IdempotencyLog.class);
        verify(idempotencyLogRepository).save(captor.capture());
        IdempotencyLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getPayloadHash()).isEqualTo(payloadHash);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
    }

    @Test
    @DisplayName("checkAndMarkProcessing should return duplicate same payload from PostgreSQL fallback")
    void checkAndMarkProcessing_shouldReturnDuplicateSamePayload_whenFallbackRecordExists() {
        UUID tenantId = UUID.randomUUID();
        String eventId = "EVT-REDIS-DOWN-2";
        String payloadHash = "hash-b";
        String responseBody = "{\"status\":\"ACCEPTED\"}";

        IdempotencyLog existing = IdempotencyLog.builder()
                .tenantId(tenantId)
                .eventId(eventId)
                .payloadHash(payloadHash)
                .status(IdempotencyStatus.COMPLETED)
                .responseBody(responseBody)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenThrow(new RuntimeException("redis down"));
        when(idempotencyLogRepository.findByTenantIdAndEventIdForUpdate(tenantId, eventId))
                .thenReturn(Optional.of(existing));

        IdempotencyService.IdempotencyCheckResult result = service.checkAndMarkProcessing(tenantId, eventId, payloadHash);

        assertThat(result.state()).isEqualTo(IdempotencyService.IdempotencyState.DUPLICATE_SAME_PAYLOAD);
        assertThat(result.cachedResponse()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("markCompleted should persist to PostgreSQL even when Redis write fails")
    void markCompleted_shouldPersistLog_whenRedisWriteFails() {
        UUID tenantId = UUID.randomUUID();
        String eventId = "EVT-REDIS-DOWN-3";
        String payloadHash = "hash-c";
        String response = "{\"ok\":true}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(eq("fis:ik:" + tenantId + ":" + eventId), any(), any());

        service.markCompleted(tenantId, eventId, payloadHash, response);

        verify(idempotencyLogRepository).save(any(IdempotencyLog.class));
    }

    @Test
    @DisplayName("checkAndMarkProcessing should retry Redis before falling back")
    void checkAndMarkProcessing_shouldRetryRedisBeforeFallback() {
        UUID tenantId = UUID.randomUUID();
        String eventId = "EVT-REDIS-RETRY-1";
        String payloadHash = "hash-retry";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any()))
                .thenThrow(new RuntimeException("redis timeout-1"))
                .thenThrow(new RuntimeException("redis timeout-2"))
                .thenReturn(Boolean.TRUE);
        when(idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)).thenReturn(Optional.empty());

        IdempotencyService.IdempotencyCheckResult result = service.checkAndMarkProcessing(tenantId, eventId, payloadHash);

        assertThat(result.state()).isEqualTo(IdempotencyService.IdempotencyState.NEW);
        verify(valueOperations, times(3)).setIfAbsent(any(), any(), any());
        verify(idempotencyLogRepository).save(any(IdempotencyLog.class));
    }
}
