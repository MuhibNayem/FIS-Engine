package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an eventId is reused with a different payload hash.
 */
public class DuplicateIdempotencyKeyException extends FisBusinessException {

    public DuplicateIdempotencyKeyException(String eventId) {
        super(
                "Duplicate eventId '" + eventId + "' was already used with a different payload hash.",
                HttpStatus.CONFLICT,
                "/problems/duplicate-idempotency-key");
    }
}
