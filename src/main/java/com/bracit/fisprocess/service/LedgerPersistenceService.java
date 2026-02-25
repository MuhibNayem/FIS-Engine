package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;

/**
 * Persists a validated draft journal entry as an immutable ledger record.
 */
public interface LedgerPersistenceService {

    /**
     * Within a single transactional boundary:
     * <ol>
     * <li>Computes the hash chain</li>
     * <li>Persists {@link JournalEntry} and all
     * {@link com.bracit.fisprocess.domain.entity.JournalLine} records</li>
     * <li>Updates account balances via {@link LedgerLockingService}</li>
     * </ol>
     *
     * @param draft the validated draft to persist
     * @return the persisted journal entry
     */
    JournalEntry persist(DraftJournalEntry draft);
}
