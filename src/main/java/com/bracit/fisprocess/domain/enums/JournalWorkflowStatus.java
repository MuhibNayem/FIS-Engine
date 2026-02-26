package com.bracit.fisprocess.domain.enums;

/**
 * Lifecycle states for manual Journal Entry approval workflow records.
 */
public enum JournalWorkflowStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
