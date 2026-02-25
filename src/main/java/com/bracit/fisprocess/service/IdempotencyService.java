package com.bracit.fisprocess.service;

import java.util.UUID;

/**
 * Idempotency contract backed by Redis + durable PostgreSQL fallback.
 */
public interface IdempotencyService {

    IdempotencyCheckResult checkAndMarkProcessing(UUID tenantId, String eventId, String payloadHash);

    void markCompleted(UUID tenantId, String eventId, String payloadHash, String responseBody);

    void markFailed(UUID tenantId, String eventId, String payloadHash, String failureDetail);

    /**
     * Result of idempotency check.
     */
    record IdempotencyCheckResult(IdempotencyState state, String cachedResponse) {
    }

    enum IdempotencyState {
        NEW,
        DUPLICATE_SAME_PAYLOAD,
        DUPLICATE_DIFFERENT_PAYLOAD
    }
}
