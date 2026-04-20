package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when attempting to finalize a bill that is already finalized.
 */
public class BillAlreadyFinalizedException extends FisBusinessException {

    public BillAlreadyFinalizedException(UUID billId) {
        super(
                "Bill '" + billId + "' is already finalized and cannot be modified.",
                HttpStatus.CONFLICT,
                "/problems/bill-already-finalized");
    }
}
