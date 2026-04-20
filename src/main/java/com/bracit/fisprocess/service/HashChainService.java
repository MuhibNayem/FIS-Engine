package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.model.DraftJournalLine;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Computes and manages the SHA-256 hash chain for tamper detection.
 */
public interface HashChainService {

    /**
     * Computes SHA-256(journalEntryId + previousHash + createdAt + linesHash).
     * The linesHash is a Merkle-style root hash of all journal lines,
     * ensuring the hash chain covers the complete entry content.
     */
    String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt,
                       List<DraftJournalLine> lines);

    /**
     * Returns the hash of the most recent journal entry for the tenant,
     * or the genesis hash {@code "0"} if no entries exist.
     */
    String getLatestHash(UUID tenantId);

    /**
     * Returns the hash of the most recent journal entry for the tenant and fiscal
     * year, or the genesis hash {@code "0"} if no entries exist in that fiscal
     * year.
     */
    default String getLatestHash(UUID tenantId, int fiscalYear) {
        return getLatestHash(tenantId);
    }
}
