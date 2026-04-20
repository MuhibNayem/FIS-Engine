package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.service.HashChainService;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SHA-256 hash chain for tamper detection on the journal entry ledger.
 * <p>
 * The hash includes a Merkle-style root of all journal lines (sorted by
 * account code), ensuring that any modification to line content (account,
 * amount, or direction) will invalidate the hash chain.
 */
@Service
@RequiredArgsConstructor
public class HashChainServiceImpl implements HashChainService {

    private static final String GENESIS_HASH = "0";

    private final JournalEntryRepository journalEntryRepository;

    @Override
    public String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt,
                              List<DraftJournalLine> lines) {
        // Compute a Merkle-style root hash of all lines (sorted for determinism)
        String linesHash = computeLinesHash(lines);

        // Include the lines hash in the entry hash to bind content to the chain
        String input = journalEntryId.toString() + previousHash + createdAt.toString() + linesHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a deterministic hash of journal lines for tamper detection.
     * Lines are sorted by account code, then by credit flag, then by amount
     * to ensure deterministic ordering regardless of input order.
     * <p>
     * Format per line: accountCode|amountCents|baseAmountCents|isCredit
     * Concatenated with ';' separator and SHA-256 hashed.
     */
    private String computeLinesHash(List<DraftJournalLine> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            String linesContent = lines.stream()
                    .sorted(Comparator.comparing(DraftJournalLine::getAccountCode)
                            .thenComparing(DraftJournalLine::isCredit)
                            .thenComparing(DraftJournalLine::getAmountCents))
                    .map(line -> String.format("%s|%d|%d|%b",
                            line.getAccountCode(),
                            line.getAmountCents(),
                            line.getBaseAmountCents() != null ? line.getBaseAmountCents() : 0L,
                            line.isCredit()))
                    .collect(Collectors.joining(";"));

            byte[] hashBytes = digest.digest(linesContent.getBytes(StandardCharsets.UTF_8));
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
