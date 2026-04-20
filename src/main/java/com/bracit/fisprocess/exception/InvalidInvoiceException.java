package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when an invoice fails validation rules (e.g., no lines, total mismatch).
 */
public class InvalidInvoiceException extends FisBusinessException {

    public InvalidInvoiceException(UUID invoiceId, String reason) {
        super(
                "Invoice '" + invoiceId + "' is invalid: " + reason,
                HttpStatus.BAD_REQUEST,
                "/problems/invalid-invoice");
    }
}
