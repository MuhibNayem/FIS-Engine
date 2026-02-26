package com.bracit.fisprocess.domain.enums;

/**
 * Status of a Journal Entry in the ledger.
 */
public enum JournalStatus {
    DRAFT,
    PENDING_APPROVAL,
    POSTED,
    REVERSAL,
    CORRECTION,
    REJECTED
}
