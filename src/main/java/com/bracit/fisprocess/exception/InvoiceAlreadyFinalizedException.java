package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when attempting to finalize an invoice that is already finalized.
 */
public class InvoiceAlreadyFinalizedException extends FisBusinessException {

    public InvoiceAlreadyFinalizedException(UUID invoiceId) {
        super(
                "Invoice '" + invoiceId + "' is already finalized and cannot be modified.",
                HttpStatus.CONFLICT,
                "/problems/invoice-already-finalized");
    }
}
