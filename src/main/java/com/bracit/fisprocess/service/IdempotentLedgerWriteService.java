package com.bracit.fisprocess.service;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reusable helper for eventId-based idempotency on ledger-writing APIs.
 */
public interface IdempotentLedgerWriteService {

    default <T> T execute(
            UUID tenantId, String eventId, Object payload, Class<T> responseType, java.util.function.Supplier<T> writeOperation) {
        return execute(tenantId, eventId, payload, responseType, writeOperation, false);
    }

    <T> T execute(
            UUID tenantId,
            String eventId,
            Object payload,
            Class<T> responseType,
            Supplier<T> writeOperation,
            boolean allowProcessingRetry);
}
