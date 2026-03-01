package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import com.bracit.fisprocess.service.IdempotencyService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis-first idempotency with durable PostgreSQL fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyServiceImpl implements IdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(72);
    private static final int REDIS_RETRY_MAX_ATTEMPTS = 3;
    private static final long REDIS_RETRY_INITIAL_BACKOFF_MS = 10L;
    private static final long REDIS_RETRY_MAX_BACKOFF_MS = 100L;

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyLogRepository idempotencyLogRepository;
    private final JsonMapper jsonMapper;
    private final @Qualifier("redisIdempotencyCircuitBreaker") CircuitBreaker redisIdempotencyCircuitBreaker;

    @Override
    @Transactional
    public IdempotencyCheckResult checkAndMarkProcessing(UUID tenantId, String eventId, String payloadHash) {
        String key = redisKey(tenantId, eventId);
        IdempotencyRecord processing = new IdempotencyRecord(IdempotencyStatus.PROCESSING, payloadHash, null);
        String processingJson = toJson(processing);

        Boolean acquired;
        try {
            acquired = executeRedisWithRetry(() ->
                    redisTemplate.opsForValue().setIfAbsent(key, processingJson, IDEMPOTENCY_TTL));
        } catch (RuntimeException redisEx) {
            log.warn("Redis unavailable for idempotency check; using PostgreSQL fallback for tenant='{}', eventId='{}'",
                    tenantId, eventId, redisEx);
            return fallbackCheckAndMarkProcessing(tenantId, eventId, payloadHash);
        }

        if (Boolean.TRUE.equals(acquired)) {
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        IdempotencyRecord existing;
        try {
            existing = fromRedisOrDatabase(tenantId, eventId, key);
        } catch (RuntimeException redisEx) {
            log.warn("Redis read failed during idempotency check; using PostgreSQL fallback for tenant='{}', eventId='{}'",
                    tenantId, eventId, redisEx);
            return fallbackCheckAndMarkProcessing(tenantId, eventId, payloadHash);
        }

        if (!payloadHash.equals(existing.getPayloadHash())) {
            return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD, null);
        }

        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            safeRedisWrite(key, processingJson);
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_SAME_PAYLOAD, existing.getResponseBody());
    }

    @Override
    public void markCompleted(UUID tenantId, String eventId, String payloadHash, String responseBody) {
        IdempotencyRecord completed = new IdempotencyRecord(IdempotencyStatus.COMPLETED, payloadHash, responseBody);
        safeRedisWrite(redisKey(tenantId, eventId), toJson(completed));
        upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.COMPLETED, responseBody);
    }

    @Override
    public void markFailed(UUID tenantId, String eventId, String payloadHash, String failureDetail) {
        IdempotencyRecord failed = new IdempotencyRecord(IdempotencyStatus.FAILED, payloadHash, failureDetail);
        safeRedisWrite(redisKey(tenantId, eventId), toJson(failed));
        upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.FAILED, failureDetail);
    }

    private IdempotencyCheckResult fallbackCheckAndMarkProcessing(UUID tenantId, String eventId, String payloadHash) {
        IdempotencyLog existing = idempotencyLogRepository.findByTenantIdAndEventIdForUpdate(tenantId, eventId)
                .orElse(null);

        if (existing == null) {
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        if (!payloadHash.equals(existing.getPayloadHash())) {
            return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD, null);
        }

        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_SAME_PAYLOAD, existing.getResponseBody());
    }

    private IdempotencyRecord fromRedisOrDatabase(UUID tenantId, String eventId, String key) {
        String redisRaw = executeRedisWithRetry(() -> redisTemplate.opsForValue().get(key));
        if (redisRaw != null) {
            return fromJson(redisRaw);
        }

        return idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)
                .map(log -> new IdempotencyRecord(log.getStatus(), log.getPayloadHash(), log.getResponseBody()))
                .orElse(new IdempotencyRecord(IdempotencyStatus.FAILED, "", null));
    }

    private void upsertLog(UUID tenantId, String eventId, String payloadHash, IdempotencyStatus status, String responseBody) {
        IdempotencyLog log = idempotencyLogRepository.findByTenantIdAndEventId(tenantId, eventId)
                .orElse(IdempotencyLog.builder().tenantId(tenantId).eventId(eventId).build());
        log.setPayloadHash(payloadHash);
        log.setStatus(status);
        log.setResponseBody(responseBody == null ? "{}" : responseBody);
        idempotencyLogRepository.save(log);
    }

    private void safeRedisWrite(String key, String value) {
        try {
            executeRedisWithRetry(() -> {
                redisTemplate.opsForValue().set(key, value, IDEMPOTENCY_TTL);
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("Redis write failed; PostgreSQL idempotency state remains authoritative for key='{}'", key, ex);
        }
    }

    private <T> T executeRedisWithRetry(Supplier<T> operation) {
        RuntimeException lastError = null;
        long backoffMs = REDIS_RETRY_INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= REDIS_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                Supplier<T> guarded = CircuitBreaker.decorateSupplier(redisIdempotencyCircuitBreaker, operation);
                return guarded.get();
            } catch (RuntimeException ex) {
                lastError = ex;
                if (attempt == REDIS_RETRY_MAX_ATTEMPTS) {
                    break;
                }
                log.debug("Redis operation failed on attempt {}/{}; retrying after {}ms",
                        attempt, REDIS_RETRY_MAX_ATTEMPTS, backoffMs, ex);
                sleepBackoff(backoffMs);
                backoffMs = Math.min(backoffMs * 2, REDIS_RETRY_MAX_BACKOFF_MS);
            }
        }

        throw lastError == null ? new IllegalStateException("Redis operation failed without an exception") : lastError;
    }

    private void sleepBackoff(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Redis retry backoff", interrupted);
        }
    }

    private String redisKey(UUID tenantId, String eventId) {
        return "fis:ik:" + tenantId + ":" + eventId;
    }

    private String toJson(IdempotencyRecord record) {
        try {
            return jsonMapper.writeValueAsString(record);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unable to serialize idempotency record", e);
        }
    }

    private IdempotencyRecord fromJson(String json) {
        try {
            return jsonMapper.readValue(json, IdempotencyRecord.class);
        } catch (RuntimeException e) {
            log.warn("Unable to parse idempotency state from Redis; treating as failed record");
            return new IdempotencyRecord(IdempotencyStatus.FAILED, "", null);
        }
    }

    @Data
    @AllArgsConstructor
    private static class IdempotencyRecord {
        private IdempotencyStatus status;
        private String payloadHash;
        private String responseBody;
    }
}
