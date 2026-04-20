package com.bracit.fisprocess.bench;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
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
 * Benchmarks double-entry accounting validation performance.
 * Measures throughput of validating journal entries with varying complexity:
 * - Simple 2-line entries (1 debit, 1 credit)
 * - Multi-line entries (5 debits, 5 credits)
 * - Wide entries (50+ lines)
 *
 * Run: ./gradlew jmh -Pjmh.include=ValidationBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class ValidationBenchmark {

    @State(Scope.Thread)
    public static class ThreadState {
        UUID tenantId = UUID.randomUUID();
        DraftJournalEntry simpleEntry;     // 2 lines (1 debit, 1 credit)
        DraftJournalEntry mediumEntry;     // 10 lines (5 debit, 5 credit)
        DraftJournalEntry wideEntry;       // 50 lines (25 debit, 25 credit)
        DraftJournalEntry unbalancedEntry; // intentionally unbalanced

        @Setup(Level.Iteration)
        public void setup() {
            simpleEntry = buildBalancedEntry(tenantId, 1, 1);
            mediumEntry = buildBalancedEntry(tenantId, 5, 5);
            wideEntry = buildBalancedEntry(tenantId, 25, 25);
            unbalancedEntry = buildUnbalancedEntry(tenantId);
        }
    }

    @Benchmark
    public void benchmarkSimpleValidation(ThreadState state, Blackhole bh) {
        boolean valid = validateDoubleEntry(state.simpleEntry);
        bh.consume(valid);
    }

    @Benchmark
    public void benchmarkMediumValidation(ThreadState state, Blackhole bh) {
        boolean valid = validateDoubleEntry(state.mediumEntry);
        bh.consume(valid);
    }

    @Benchmark
    public void benchmarkWideValidation(ThreadState state, Blackhole bh) {
        boolean valid = validateDoubleEntry(state.wideEntry);
        bh.consume(valid);
    }

    @Benchmark
    public void benchmarkUnbalancedDetection(ThreadState state, Blackhole bh) {
        boolean balanced = validateDoubleEntry(state.unbalancedEntry);
        bh.consume(balanced);
    }

    @Benchmark
    public void benchmarkDebitCreditCounting(ThreadState state, Blackhole bh) {
        long debitCount = state.mediumEntry.getLines().stream()
                .filter(line -> !line.isCredit())
                .count();
        long creditCount = state.mediumEntry.getLines().stream()
                .filter(DraftJournalLine::isCredit)
                .count();
        bh.consume(debitCount + creditCount);
    }

    @Benchmark
    public void benchmarkAmountSummation(ThreadState state, Blackhole bh) {
        long totalDebits = state.mediumEntry.getLines().stream()
                .filter(line -> !line.isCredit())
                .mapToLong(DraftJournalLine::getAmountCents)
                .sum();
        long totalCredits = state.mediumEntry.getLines().stream()
                .filter(DraftJournalLine::isCredit)
                .mapToLong(DraftJournalLine::getAmountCents)
                .sum();
        bh.consume(totalDebits);
        bh.consume(totalCredits);
    }

    /**
     * Core double-entry validation: checks that debits == credits,
     * at least one of each exists, and total is non-zero.
     */
    private static boolean validateDoubleEntry(DraftJournalEntry draft) {
        long totalDebits = 0;
        long totalCredits = 0;
        long debitLineCount = 0;
        long creditLineCount = 0;

        for (DraftJournalLine line : draft.getLines()) {
            if (line.isCredit()) {
                totalCredits += line.getAmountCents();
                creditLineCount++;
            } else {
                totalDebits += line.getAmountCents();
                debitLineCount++;
            }
        }

        if (debitLineCount == 0 || creditLineCount == 0) {
            return false; // Must have at least one debit and one credit
        }

        if (totalDebits != totalCredits) {
            return false; // Debits must equal credits
        }

        if (totalDebits == 0) {
            return false; // Total must be non-zero
        }

        return true;
    }

    /**
     * Validates account existence and active status (simulated).
     */
    @Benchmark
    public void benchmarkAccountValidation(ThreadState state, Blackhole bh) {
        // Simulate account lookup for each line
        for (DraftJournalLine line : state.mediumEntry.getLines()) {
            // In production: accountRepository.findByTenantIdAndCode(tenantId, code)
            boolean exists = line.getAccountCode() != null && !line.getAccountCode().isEmpty();
            boolean isActive = true; // simulated
            bh.consume(exists && isActive);
        }
    }

    /**
     * Validates currency consistency across journal lines.
     */
    @Benchmark
    public void benchmarkCurrencyValidation(ThreadState state, Blackhole bh) {
        String entryCurrency = state.mediumEntry.getTransactionCurrency();
        boolean allMatch = true;
        for (DraftJournalLine line : state.mediumEntry.getLines()) {
            // Simulated: in production, account.getCurrencyCode() == entryCurrency
            String lineCurrency = "USD"; // simulated account currency
            if (!lineCurrency.equals(entryCurrency)) {
                allMatch = false;
                break;
            }
        }
        bh.consume(allMatch);
    }

    private static DraftJournalEntry buildBalancedEntry(UUID tenantId, int debitLines, int creditLines) {
        DraftJournalEntry entry = new DraftJournalEntry();
        entry.setTenantId(tenantId);
        entry.setEventId(UUID.randomUUID().toString());
        entry.setPostedDate(OffsetDateTime.now());
        entry.setEffectiveDate(LocalDate.now());
        entry.setTransactionDate(LocalDate.now());
        entry.setDescription("Benchmark entry");
        entry.setReferenceId("BENCH");
        entry.setTransactionCurrency("USD");
        entry.setBaseCurrency("USD");
        entry.setExchangeRate(BigDecimal.ONE);
        entry.setCreatedBy("benchmark");
        entry.setAutoReverse(false);

        List<DraftJournalLine> lines = new ArrayList<>(debitLines + creditLines);
        long totalAmount = 10_000_00L; // $10,000.00
        long debitAmount = totalAmount / debitLines;
        long creditAmount = totalAmount / creditLines;

        for (int i = 0; i < debitLines; i++) {
            DraftJournalLine line = new DraftJournalLine();
            line.setAccountCode("ASSET-" + (1000 + i));
            line.setAmountCents(debitAmount);
            line.setBaseAmountCents(debitAmount);
            line.setCredit(false);
            lines.add(line);
        }

        for (int i = 0; i < creditLines; i++) {
            DraftJournalLine line = new DraftJournalLine();
            line.setAccountCode("REV-" + (4000 + i));
            line.setAmountCents(creditAmount);
            line.setBaseAmountCents(creditAmount);
            line.setCredit(true);
            lines.add(line);
        }

        entry.setLines(lines);
        return entry;
    }

    private static DraftJournalEntry buildUnbalancedEntry(UUID tenantId) {
        DraftJournalEntry entry = buildBalancedEntry(tenantId, 2, 2);
        // Make it unbalanced by changing one line's amount
        DraftJournalLine firstLine = entry.getLines().get(0);
        firstLine.setAmountCents(firstLine.getAmountCents() + 1); // Off by 1 cent
        return entry;
    }
}
