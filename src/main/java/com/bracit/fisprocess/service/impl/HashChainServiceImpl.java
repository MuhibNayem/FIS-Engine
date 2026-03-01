package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.HashChainService;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * SHA-256 hash chain for tamper detection on the journal entry ledger.
 */
@Service
@RequiredArgsConstructor
public class HashChainServiceImpl implements HashChainService {

    private static final String GENESIS_HASH = "0";

    private final JournalEntryRepository journalEntryRepository;

    @Override
    public String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt) {
        String input = journalEntryId.toString() + previousHash + createdAt.toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public String getLatestHash(UUID tenantId) {
        return journalEntryRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
                .map(je -> je.getHash())
                .orElse(GENESIS_HASH);
    }

    @Override
    public String getLatestHash(UUID tenantId, int fiscalYear) {
        return journalEntryRepository.findTopByTenantIdAndFiscalYearOrderBySequenceNumberDesc(tenantId, fiscalYear)
                .map(je -> je.getHash())
                .orElse(GENESIS_HASH);
    }
}
