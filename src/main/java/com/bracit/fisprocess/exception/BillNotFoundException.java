package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a bill with the requested ID cannot be found within the tenant.
 */
public class BillNotFoundException extends FisBusinessException {

    public BillNotFoundException(UUID billId) {
        super(
                "Bill with ID '" + billId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/bill-not-found");
    }
}
