package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when an invoice with the requested ID cannot be found within the tenant.
 */
public class InvoiceNotFoundException extends FisBusinessException {

    public InvoiceNotFoundException(UUID invoiceId) {
        super(
                "Invoice with ID '" + invoiceId + "' not found.",
                HttpStatus.NOT_FOUND,
                "/problems/invoice-not-found");
    }
}
