package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;

import java.util.List;

/**
 * Persists validated draft journal entries as immutable ledger records.
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

    /**
     * Batch persist multiple draft journal entries efficiently.
     * Uses batch insert for journal entries and lines, and aggregates
     * account balance updates.
     *
     * @param drafts the validated drafts to persist
     * @return the persisted journal entries
     */
    List<JournalEntry> persistBatch(List<DraftJournalEntry> drafts);
}