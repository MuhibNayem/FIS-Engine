package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;

/**
 * Validates that a draft Journal Entry satisfies double-entry accounting rules.
 */
public interface JournalEntryValidationService {

    /**
     * Validates the draft:
     * <ul>
     * <li>Sum(debit amounts) == Sum(credit amounts)</li>
     * <li>All referenced account codes exist and are active</li>
     * <li>Each line has amountCents &gt; 0</li>
     * <li>At least one debit and one credit line</li>
     * </ul>
     *
     * @throws com.bracit.fisprocess.exception.UnbalancedEntryException if debits !=
     *                                                                  credits
     * @throws com.bracit.fisprocess.exception.AccountNotFoundException if account
     *                                                                  code not
     *                                                                  found
     * @throws com.bracit.fisprocess.exception.InactiveAccountException if account
     *                                                                  is inactive
     */
    void validate(DraftJournalEntry draft);
}
