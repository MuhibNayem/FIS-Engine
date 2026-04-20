package com.bracit.fisprocess.bench;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks idempotency check performance using an in-memory store
 * that simulates Redis SETNX behavior.
 *
 * Measures:
 * - First-time check + mark processing (SETNX path)
 * - Duplicate detection (key exists path)
 * - Concurrent duplicate check contention
 *
 * Run: ./gradlew jmh -Pjmh.include=IdempotencyBenchmark
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Benchmark)
public class IdempotencyBenchmark {

    private static final String KEY_PREFIX = "fis:ik:";

    @State(Scope.Thread)
    public static class ThreadState {
        UUID tenantId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String payloadHash = "payload-hash-" + UUID.randomUUID();
        Map<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    }

    @State(Scope.Benchmark)
    public static class SharedState {
        // Simulates a shared Redis instance across threads
        Map<String, IdempotencyRecord> sharedStore = new ConcurrentHashMap<>();
        List<String> eventIds = new ArrayList<>();

        @Setup(Level.Iteration)
        public void setup() {
            sharedStore.clear();
            eventIds.clear();
            for (int i = 0; i < 1000; i++) {
                eventIds.add(UUID.randomUUID().toString());
            }
        }
    }

    /**
     * Benchmark: first-time idempotency check (key doesn't exist, SETNX succeeds).
     * This is the cold path for new events.
     */
    @Benchmark
    public IdempotencyCheckResult benchmarkFirstTimeCheck(ThreadState state, Blackhole bh) {
        String key = KEY_PREFIX + state.tenantId + ":" + state.eventId;
        IdempotencyRecord record = new IdempotencyRecord("PROCESSING", state.payloadHash);

        // Simulate Redis SETNX
        IdempotencyRecord existing = state.store.putIfAbsent(key, record);
        if (existing == null) {
            // SETNX succeeded - this is a new event
            bh.consume(IdempotencyCheckResult.NEW);
            return IdempotencyCheckResult.NEW;
        }
        bh.consume(IdempotencyCheckResult.DUPLICATE);
        return IdempotencyCheckResult.DUPLICATE;
    }

    /**
     * Benchmark: duplicate detection (key already exists).
     * This is the hot path for retry scenarios.
     */
    @Benchmark
    public IdempotencyCheckResult benchmarkDuplicateCheck(ThreadState state) {
        String key = KEY_PREFIX + state.tenantId + ":" + state.eventId;
        IdempotencyRecord record = new IdempotencyRecord("PROCESSING", state.payloadHash);

        // Pre-populate to force the duplicate path
        state.store.putIfAbsent(key, record);

        // Now check again
        IdempotencyRecord existing = state.store.get(key);
        if (existing != null) {
            if (existing.payloadHash.equals(state.payloadHash)) {
                return IdempotencyCheckResult.DUPLICATE_SAME_PAYLOAD;
            } else {
                return IdempotencyCheckResult.DUPLICATE_DIFFERENT_PAYLOAD;
            }
        }
        return IdempotencyCheckResult.NEW;
    }

    /**
     * Benchmark: concurrent idempotency checks across many unique events.
     * Simulates high-throughput production traffic.
     */
    @Benchmark
    public IdempotencyCheckResult benchmarkConcurrentChecks(ThreadState state, SharedState shared, Blackhole bh) {
        // Rotate through event IDs to simulate realistic traffic patterns
        String eventId = shared.eventIds.get(ThreadLocalRandom.current().nextInt(shared.eventIds.size()));
        String key = KEY_PREFIX + state.tenantId + ":" + eventId;
        String hash = "hash-" + eventId;

        IdempotencyRecord newRecord = new IdempotencyRecord("PROCESSING", hash);
        IdempotencyRecord existing = shared.sharedStore.putIfAbsent(key, newRecord);

        if (existing == null) {
            bh.consume(IdempotencyCheckResult.NEW);
            return IdempotencyCheckResult.NEW;
        }

        if (existing.payloadHash.equals(hash)) {
            bh.consume(IdempotencyCheckResult.DUPLICATE_SAME_PAYLOAD);
            return IdempotencyCheckResult.DUPLICATE_SAME_PAYLOAD;
        }

        bh.consume(IdempotencyCheckResult.DUPLICATE_DIFFERENT_PAYLOAD);
        return IdempotencyCheckResult.DUPLICATE_DIFFERENT_PAYLOAD;
    }

    /**
     * Benchmark: key serialization overhead (tenantId + eventId → Redis key).
     */
    @Benchmark
    public void benchmarkKeySerialization(ThreadState state, Blackhole bh) {
        String key = KEY_PREFIX + state.tenantId + ":" + state.eventId;
        bh.consume(key);
    }

    /**
     * Benchmark: record serialization (JSON encode/decode simulation).
     */
    @Benchmark
    public void benchmarkRecordSerialization(ThreadState state, Blackhole bh) {
        IdempotencyRecord record = new IdempotencyRecord("COMPLETED", state.payloadHash, "{\"status\":\"ok\"}");
        // Simple simulation of JSON serialization
        String json = "{\"status\":\"" + record.status + "\",\"hash\":\"" + record.payloadHash + "\"}";
        bh.consume(json);
    }

    public enum IdempotencyCheckResult {
        NEW,
        DUPLICATE,
        DUPLICATE_SAME_PAYLOAD,
        DUPLICATE_DIFFERENT_PAYLOAD
    }

    public static class IdempotencyRecord {
        final String status;
        final String payloadHash;
        final String responseBody;

        IdempotencyRecord(String status, String payloadHash) {
            this(status, payloadHash, null);
        }

        IdempotencyRecord(String status, String payloadHash, String responseBody) {
            this.status = status;
            this.payloadHash = payloadHash;
            this.responseBody = responseBody;
        }
    }
}
