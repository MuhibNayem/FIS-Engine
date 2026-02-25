package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import com.bracit.fisprocess.domain.enums.IdempotencyStatus;
import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import com.bracit.fisprocess.service.IdempotencyService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-first idempotency with durable PostgreSQL fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyServiceImpl implements IdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(72);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyLogRepository idempotencyLogRepository;
    private final JsonMapper jsonMapper;

    @Override
    public IdempotencyCheckResult checkAndMarkProcessing(UUID tenantId, String eventId, String payloadHash) {
        String key = redisKey(tenantId, eventId);
        IdempotencyRecord processing = new IdempotencyRecord(IdempotencyStatus.PROCESSING, payloadHash, null);
        String processingJson = toJson(processing);

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, processingJson, IDEMPOTENCY_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        IdempotencyRecord existing = fromRedisOrDatabase(tenantId, eventId, key);
        if (!payloadHash.equals(existing.getPayloadHash())) {
            return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD, null);
        }

        if (existing.getStatus() == IdempotencyStatus.FAILED) {
            redisTemplate.opsForValue().set(key, processingJson, IDEMPOTENCY_TTL);
            upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.PROCESSING, "{}");
            return new IdempotencyCheckResult(IdempotencyState.NEW, null);
        }

        return new IdempotencyCheckResult(IdempotencyState.DUPLICATE_SAME_PAYLOAD, existing.getResponseBody());
    }

    @Override
    public void markCompleted(UUID tenantId, String eventId, String payloadHash, String responseBody) {
        IdempotencyRecord completed = new IdempotencyRecord(IdempotencyStatus.COMPLETED, payloadHash, responseBody);
        redisTemplate.opsForValue().set(redisKey(tenantId, eventId), toJson(completed), IDEMPOTENCY_TTL);
        upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.COMPLETED, responseBody);
    }

    @Override
    public void markFailed(UUID tenantId, String eventId, String payloadHash, String failureDetail) {
        IdempotencyRecord failed = new IdempotencyRecord(IdempotencyStatus.FAILED, payloadHash, failureDetail);
        redisTemplate.opsForValue().set(redisKey(tenantId, eventId), toJson(failed), IDEMPOTENCY_TTL);
        upsertLog(tenantId, eventId, payloadHash, IdempotencyStatus.FAILED, failureDetail);
    }

    private IdempotencyRecord fromRedisOrDatabase(UUID tenantId, String eventId, String key) {
        String redisRaw = redisTemplate.opsForValue().get(key);
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
