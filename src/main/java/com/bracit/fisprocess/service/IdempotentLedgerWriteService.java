package com.bracit.fisprocess.service;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reusable helper for eventId-based idempotency on ledger-writing APIs.
 */
public interface IdempotentLedgerWriteService {

    <T> T execute(
            UUID tenantId, String eventId, Object payload, Class<T> responseType, Supplier<T> writeOperation);
}
