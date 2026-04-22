package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class WriteCoalescer {

    private final Map<CoalescingKey, CoalescingBucket> buckets = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @Value("${fis.coalescer.window-ms:10}")
    private long windowMs;

    @Value("${fis.coalescer.max-batch-size:100}")
    private int maxBatchSize;

    @Value("${fis.coalescer.enabled:true}")
    private boolean enabled;

    public interface BatchHandler {
        List<JournalEntryResponseDto> handleBatch(List<DraftJournalEntry> entries, UUID tenantId);
    }

    @PostConstruct
    public void initialize() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "write-coalescer-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flushAll, windowMs, windowMs, TimeUnit.MILLISECONDS);
        log.info("WriteCoalescer initialized with window={}ms, maxBatch={}", windowMs, maxBatchSize);
    }

    public void submit(DraftJournalEntry entry, UUID tenantId, BatchHandler handler) {
        if (!enabled) {
            handler.handleBatch(List.of(entry), tenantId);
            return;
        }

        CoalescingKey key = new CoalescingKey(tenantId, extractAccountCode(entry));
        CoalescingBucket bucket = buckets.computeIfAbsent(key, k -> new CoalescingBucket(handler, tenantId));

        synchronized (bucket) {
            bucket.add(entry);
            if (bucket.size() >= maxBatchSize) {
                flushBucket(key, bucket);
            }
        }
    }

    private void flushBucket(CoalescingKey key, CoalescingBucket bucket) {
        synchronized (bucket) {
            if (bucket.isFlushing()) {
                return;
            }
            bucket.markFlushing();
        }

        try {
            List<DraftJournalEntry> batch = bucket.drain();
            if (!batch.isEmpty()) {
                log.debug("Flushing {} entries for key {}", batch.size(), key);
                bucket.invokeHandler(batch);
            }
        } finally {
            bucket.markReady();
        }
    }

    private void flushAll() {
        List<Map.Entry<CoalescingKey, CoalescingBucket>> snapshot = new ArrayList<>(buckets.entrySet());
        for (Map.Entry<CoalescingKey, CoalescingBucket> entry : snapshot) {
            CoalescingBucket bucket = entry.getValue();
            boolean shouldFlush;
            synchronized (bucket) {
                shouldFlush = bucket.shouldFlush(windowMs);
            }
            if (shouldFlush) {
                flushBucket(entry.getKey(), bucket);
            }
        }
    }

    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        flushAll();
    }

    private String extractAccountCode(DraftJournalEntry entry) {
        return entry.getLines().isEmpty() ? "UNKNOWN" : entry.getLines().getFirst().getAccountCode();
    }

    public record CoalescingKey(UUID tenantId, String accountCode) {
    }

    private static class CoalescingBucket {
        private final BatchHandler handler;
        private final UUID tenantId;
        private final List<DraftJournalEntry> pending = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private volatile long firstSubmissionTime;
        private volatile boolean flushing;

        CoalescingBucket(BatchHandler handler, UUID tenantId) {
            this.handler = handler;
            this.tenantId = tenantId;
        }

        synchronized void add(DraftJournalEntry entry) {
            if (firstSubmissionTime == 0) {
                firstSubmissionTime = System.currentTimeMillis();
            }
            pending.add(entry);
        }

        int size() {
            return pending.size();
        }

        boolean shouldFlush(long windowMs) {
            return !pending.isEmpty() && (System.currentTimeMillis() - firstSubmissionTime) >= windowMs;
        }

        boolean isFlushing() {
            return flushing;
        }

        void markFlushing() {
            flushing = true;
        }

        void markReady() {
            flushing = false;
        }

        List<DraftJournalEntry> drain() {
            synchronized (this) {
                List<DraftJournalEntry> batch = new ArrayList<>(pending);
                pending.clear();
                firstSubmissionTime = 0;
                return batch;
            }
        }

        void invokeHandler(List<DraftJournalEntry> batch) {
            try {
                handler.handleBatch(batch, tenantId);
            } catch (Exception e) {
                log.error("Error invoking write handler for batch of {} entries", batch.size(), e);
            }
        }
    }
}