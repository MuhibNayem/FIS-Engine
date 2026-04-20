package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a bill fails validation rules (e.g., no lines, total mismatch).
 */
public class InvalidBillException extends FisBusinessException {

    public InvalidBillException(UUID billId, String reason) {
        super(
                "Bill '" + billId + "' is invalid: " + reason,
                HttpStatus.BAD_REQUEST,
                "/problems/invalid-bill");
    }
}
