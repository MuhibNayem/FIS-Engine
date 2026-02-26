package com.bracit.fisprocess.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a Journal Entry's debits do not equal its credits.
 */
public class UnbalancedEntryException extends FisBusinessException {

    public UnbalancedEntryException(long totalDebits, long totalCredits) {
        this(String.format("Journal entry is unbalanced: total debits = %d, total credits = %d",
                totalDebits, totalCredits));
    }

    public UnbalancedEntryException(String detailMessage) {
        super(detailMessage, HttpStatus.UNPROCESSABLE_ENTITY, "/problems/unbalanced-entry");
    }
}
