package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Thrown when a debit note amount exceeds the original bill's outstanding amount.
 */
public class DebitNoteExceedsBillException extends FisBusinessException {

    public DebitNoteExceedsBillException(UUID debitNoteId, UUID billId) {
        super(
                "Debit note '" + debitNoteId + "' amount exceeds outstanding balance of bill '" + billId + "'.",
                HttpStatus.BAD_REQUEST,
                "/problems/debit-note-exceeds-bill");
    }
}
