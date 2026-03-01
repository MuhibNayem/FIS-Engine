package com.bracit.fisprocess.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Computes and manages the SHA-256 hash chain for tamper detection.
 */
public interface HashChainService {

    /**
     * Computes SHA-256(journalEntryId + previousHash + createdAt).
     */
    String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt);

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
