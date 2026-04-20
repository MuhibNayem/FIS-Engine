package com.bracit.fisprocess.bench;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.service.HashChainService;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks the journal posting engine end-to-end:
 * validation → hash chain → persistence simulation.
 *
 * Run: ./gradlew jmh
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class JournalPostingEngineBenchmark {

    @State(Scope.Thread)
    public static class BenchmarkState {
        UUID tenantId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        OffsetDateTime postedDate = OffsetDateTime.now();
        int fiscalYear = postedDate.getYear();

        // Reusable draft journal entry with 2 lines (balanced)
        DraftJournalEntry simpleEntry;

        // Reusable draft journal entry with 10 lines (complex)
        DraftJournalEntry complexEntry;

        @Setup(Level.Iteration)
        public void setup() {
            simpleEntry = buildBalancedJournal(tenantId, 2);
            complexEntry = buildBalancedJournal(tenantId, 10);
        }
    }

    @Benchmark
    public void benchmarkSimpleJournalPosting(SimplePostingState state, Blackhole bh) {
        // Simulate the core posting pipeline: validate → hash → persist
        JournalEntryValidationService validation = new InlineValidationBenchmark();
        HashChainService hashChain = new InlineHashChainBenchmark();

        validation.validate(state.simpleEntry);
        String hash = hashChain.computeHash(
                UUID.randomUUID(),
                "previous-hash-" + state.fiscalYear,
                state.postedDate,
                state.simpleEntry.getLines()
        );
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkComplexJournalPosting(SimplePostingState state, Blackhole bh) {
        JournalEntryValidationService validation = new InlineValidationBenchmark();
        HashChainService hashChain = new InlineHashChainBenchmark();

        validation.validate(state.complexEntry);
        String hash = hashChain.computeHash(
                UUID.randomUUID(),
                "previous-hash-" + state.fiscalYear,
                state.postedDate,
                state.complexEntry.getLines()
        );
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkJournalEntrySerialization(SimplePostingState state, Blackhole bh) {
        // Measure overhead of building a JournalEntry entity from a draft
        JournalEntry entry = buildJournalEntryFromDraft(state.simpleEntry);
        bh.consume(entry);
    }

    private static DraftJournalEntry buildBalancedJournal(UUID tenantId, int numLines) {
        long amountPerLine = 1_000_00L; // $1,000.00 in cents
        long totalAmount = amountPerLine * numLines;
        long halfDebit = totalAmount / 2;
        long halfCredit = totalAmount - halfDebit;

        DraftJournalEntry entry = new DraftJournalEntry();
        entry.setTenantId(tenantId);
        entry.setEventId(UUID.randomUUID().toString());
        entry.setPostedDate(OffsetDateTime.now());
        entry.setEffectiveDate(LocalDate.now());
        entry.setTransactionDate(LocalDate.now());
        entry.setDescription("Benchmark journal entry");
        entry.setReferenceId("BENCH-REF");
        entry.setTransactionCurrency("USD");
        entry.setBaseCurrency("USD");
        entry.setExchangeRate(BigDecimal.ONE);
        entry.setCreatedBy("benchmark");
        entry.setAutoReverse(false);

        List<DraftJournalLine> lines = new ArrayList<>();
        int halfLines = numLines / 2;

        // Debit lines
        for (int i = 0; i < halfLines; i++) {
            DraftJournalLine line = new DraftJournalLine();
            line.setAccountCode("ASSET-1000-" + i);
            line.setAmountCents(halfDebit / halfLines);
            line.setBaseAmountCents(halfDebit / halfLines);
            line.setCredit(false);
            lines.add(line);
        }

        // Credit lines
        for (int i = 0; i < numLines - halfLines; i++) {
            DraftJournalLine line = new DraftJournalLine();
            line.setAccountCode("REV-4000-" + i);
            line.setAmountCents(halfCredit / (numLines - halfLines));
            line.setBaseAmountCents(halfCredit / (numLines - halfLines));
            line.setCredit(true);
            lines.add(line);
        }

        entry.setLines(lines);
        return entry;
    }

    private static JournalEntry buildJournalEntryFromDraft(DraftJournalEntry draft) {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();

        return JournalEntry.builder()
                .id(id)
                .tenantId(draft.getTenantId())
                .eventId(draft.getEventId())
                .postedDate(draft.getPostedDate())
                .effectiveDate(draft.getEffectiveDate())
                .transactionDate(draft.getTransactionDate())
                .description(draft.getDescription())
                .referenceId(draft.getReferenceId())
                .status(JournalStatus.POSTED)
                .transactionCurrency(draft.getTransactionCurrency())
                .baseCurrency(draft.getBaseCurrency())
                .exchangeRate(draft.getExchangeRate())
                .createdBy(draft.getCreatedBy())
                .createdAt(createdAt)
                .previousHash("genesis")
                .hash("computed-hash")
                .fiscalYear(draft.getPostedDate().getYear())
                .sequenceNumber(1L)
                .autoReverse(draft.isAutoReverse())
                .build();
    }

    /**
     * Inline validation implementation for benchmarking (no Spring dependencies).
     */
    static class InlineValidationBenchmark implements JournalEntryValidationService {
        @Override
        public void validate(DraftJournalEntry draft) {
            long totalDebits = draft.getLines().stream()
                    .filter(line -> !line.isCredit())
                    .mapToLong(DraftJournalLine::getAmountCents)
                    .sum();

            long totalCredits = draft.getLines().stream()
                    .filter(DraftJournalLine::isCredit)
                    .mapToLong(DraftJournalLine::getAmountCents)
                    .sum();

            if (totalDebits != totalCredits) {
                throw new IllegalStateException("Unbalanced entry: debits=" + totalDebits + ", credits=" + totalCredits);
            }
        }
    }

    /**
     * Inline hash chain implementation for benchmarking (no Spring dependencies).
     */
    static class InlineHashChainBenchmark implements HashChainService {
        @Override
        public String computeHash(UUID journalEntryId, String previousHash, OffsetDateTime createdAt,
                                  java.util.List<DraftJournalLine> lines) {
            String input = journalEntryId.toString() + previousHash + createdAt.toString() + lines.size();
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.HexFormat.of().formatHex(hashBytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }

        @Override
        public String getLatestHash(UUID tenantId) {
            return "genesis";
        }

        @Override
        public String getLatestHash(UUID tenantId, int fiscalYear) {
            return "genesis";
        }
    }

    // Helper state classes
    @State(Scope.Thread)
    public static class SimplePostingState {
        UUID tenantId = UUID.randomUUID();
        int fiscalYear = OffsetDateTime.now().getYear();
        OffsetDateTime postedDate = OffsetDateTime.now();
        DraftJournalEntry simpleEntry;
        DraftJournalEntry complexEntry;

        @Setup(Level.Iteration)
        public void setup() {
            simpleEntry = buildBalancedJournal(tenantId, 2);
            complexEntry = buildBalancedJournal(tenantId, 10);
        }
    }
}
