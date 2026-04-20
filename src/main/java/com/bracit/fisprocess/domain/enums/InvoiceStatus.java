package com.bracit.fisprocess.domain.enums;

/**
 * Lifecycle status of an AR Invoice.
 */
public enum InvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    WRITTEN_OFF
}
