package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a credit note amount exceeds the original invoice's outstanding amount.
 */
public class CreditNoteExceedsInvoiceException extends FisBusinessException {

    public CreditNoteExceedsInvoiceException(UUID creditNoteId, UUID invoiceId) {
        super(
                "Credit note '" + creditNoteId + "' amount exceeds outstanding balance of invoice '" + invoiceId + "'.",
                HttpStatus.BAD_REQUEST,
                "/problems/credit-note-exceeds-invoice");
    }
}
