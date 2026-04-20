package com.bracit.fisprocess.bench;

import com.bracit.fisprocess.domain.model.DraftJournalLine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Benchmarks the SHA-256 hash chain computation used for tamper detection.
 * Measures throughput of single entry hashing, Merkle-style line hashing,
 * and full chain verification scenarios.
 *
 * Run: ./gradlew jmh -Pjmh.include=HashChainBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class HashChainBenchmark {

    private static final String GENESIS_HASH = "0";
    private static final String PREVIOUS_HASH = "abc123def456789012345678901234567890123456789012345678901234";

    @State(Scope.Thread)
    public static class ThreadState {
        UUID journalEntryId = UUID.randomUUID();
        List<DraftJournalLine> smallLines = buildLines(2);
        List<DraftJournalLine> mediumLines = buildLines(10);
        List<DraftJournalLine> largeLines = buildLines(100);

        MessageDigest sha256;

        @Setup(Level.Iteration)
        public void setup() throws NoSuchAlgorithmException {
            sha256 = MessageDigest.getInstance("SHA-256");
        }
    }

    @Benchmark
    public void benchmarkSmallEntryHash(ThreadState state, Blackhole bh) {
        String hash = computeEntryHash(state.sha256, state.journalEntryId, PREVIOUS_HASH, state.smallLines);
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkMediumEntryHash(ThreadState state, Blackhole bh) {
        String hash = computeEntryHash(state.sha256, state.journalEntryId, PREVIOUS_HASH, state.mediumLines);
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkLargeEntryHash(ThreadState state, Blackhole bh) {
        String hash = computeEntryHash(state.sha256, state.journalEntryId, PREVIOUS_HASH, state.largeLines);
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkLinesHashOnly(ThreadState state, Blackhole bh) {
        String hash = computeLinesHash(state.sha256, state.mediumLines);
        bh.consume(hash);
    }

    @Benchmark
    public void benchmarkChainContinuity(ThreadState state, Blackhole bh) {
        // Simulate chaining: hash_N depends on hash_N-1
        String currentHash = GENESIS_HASH;
        for (int i = 0; i < 100; i++) {
            String input = UUID.randomUUID().toString() + currentHash + System.nanoTime();
            currentHash = sha256Hex(state.sha256, input);
        }
        bh.consume(currentHash);
    }

    /**
     * Computes the full entry hash including all journal lines.
     */
    private static String computeEntryHash(MessageDigest digest, UUID entryId, String previousHash,
                                            List<DraftJournalLine> lines) {
        String linesHash = computeLinesHash(digest, lines);
        String input = entryId.toString() + previousHash + System.nanoTime() + linesHash;
        return sha256Hex(digest, input);
    }

    /**
     * Computes a deterministic hash of journal lines (Merkle-style root).
     */
    private static String computeLinesHash(MessageDigest digest, List<DraftJournalLine> lines) {
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

        return sha256Hex(digest, linesContent);
    }

    private static String sha256Hex(MessageDigest digest, String input) {
        // Clone the digest since it's stateful
        try {
            MessageDigest cloned = (MessageDigest) digest.clone();
            byte[] hashBytes = cloned.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("MessageDigest not cloneable", e);
        }
    }

    private static List<DraftJournalLine> buildLines(int count) {
        List<DraftJournalLine> lines = new ArrayList<>(count);
        long totalAmount = 10_000_00L; // $10,000.00
        long amountPerLine = totalAmount / count;
        int halfCount = count / 2;

        for (int i = 0; i < count; i++) {
            DraftJournalLine line = new DraftJournalLine();
            line.setAccountCode(i < halfCount ? "ASSET-" + (1000 + i) : "REV-" + (4000 + i));
            line.setAmountCents(amountPerLine);
            line.setBaseAmountCents(amountPerLine);
            line.setCredit(i >= halfCount);
            lines.add(line);
        }
        return lines;
    }
}
