package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.JournalEntry;

import java.util.UUID;

/**
 * Writes and relays transactional outbox events.
 */
public interface OutboxService {

    void recordJournalPosted(UUID tenantId, String sourceEventId, JournalEntry journalEntry);

    void relayUnpublished();
}
