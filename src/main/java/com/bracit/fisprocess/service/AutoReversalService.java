package com.bracit.fisprocess.service;

import java.util.UUID;

/**
 * Service for generating automatic reversal entries for accrual journal entries
 * flagged with {@code autoReverse = true}.
 * <p>
 * Triggered when an accounting period is reopened or when a new period opens,
 * generating reversals for all auto-reverse JEs from the prior period.
 */
public interface AutoReversalService {

    /**
     * Generates reversal entries for all auto-reversible journal entries
     * posted within the specified period's date range.
     *
     * @param tenantId  the tenant UUID
     * @param periodId  the period that was just opened (reversals are dated to its
     *                  start)
     * @param createdBy the actor performing the operation
     * @return the number of reversal entries generated
     */
    int generateReversals(UUID tenantId, UUID periodId, String createdBy);
}
