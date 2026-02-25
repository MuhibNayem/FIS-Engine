package com.bracit.fisprocess.domain.enums;

/**
 * Status of an idempotency log entry.
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
