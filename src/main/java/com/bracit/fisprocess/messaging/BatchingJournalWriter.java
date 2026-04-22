package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.AsyncJobStatusService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.ShardRouter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
@RequiredArgsConstructor
@Slf4j
public class BatchingJournalWriter {

    private final JournalWriteMessageMapper messageMapper;
    private final BusinessEntityRepository businessEntityRepository;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final AsyncJobStatusService asyncJobStatusService;
    private final ShardRouter shardRouter;
    private final MeterRegistry meterRegistry;

    @Value("${fis.batch.max-size:100}")
    private int maxBatchSize;

    @Value("${fis.batch.flush-interval-ms:10}")
    private long flushIntervalMs;

    @Value("${fis.batch.enabled:false}")
    private boolean batchEnabled;

    private final Map<UUID, BatchBucket> tenantBuckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = true;

    public void initialize() {
        if (!batchEnabled) {
            log.info("BatchingJournalWriter disabled");
            return;
        }

        scheduler.scheduleAtFixedRate(this::flushAll, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        log.info("BatchingJournalWriter initialized with maxSize={}, flushInterval={}ms",
                maxBatchSize, flushIntervalMs);
    }

    private static final int ABSOLUTE_MAX_BATCH_SIZE = 1000;

    public List<JournalEntryResponseDto> submitBatch(List<JournalWriteMessage> messages) {
        if (!batchEnabled || messages.isEmpty()) {
            return processIndividually(messages);
        }

        if (messages.size() > ABSOLUTE_MAX_BATCH_SIZE) {
            log.warn("Batch size {} exceeds absolute max {}, truncating", messages.size(), ABSOLUTE_MAX_BATCH_SIZE);
            messages = messages.subList(0, ABSOLUTE_MAX_BATCH_SIZE);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        List<DraftJournalEntry> drafts = new ArrayList<>();
        List<UUID> trackingIds = new ArrayList<>();

        try {
            for (JournalWriteMessage message : messages) {
                validateMessage(message);
                BusinessEntity tenant = businessEntityRepository.findById(message.getTenantId())
                        .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + message.getTenantId()));

                DraftJournalEntry draft = buildDraft(message.getTenantId(), tenant, message.getRequest());
                drafts.add(draft);
                trackingIds.add(message.getTrackingId());
            }

            List<com.bracit.fisprocess.domain.entity.JournalEntry> entries =
                    ledgerPersistenceService.persistBatch(drafts);

            List<JournalEntryResponseDto> results = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                UUID trackingId = trackingIds.get(i);
                JournalEntryResponseDto dto = toResponseDto(entries.get(i));
                asyncJobStatusService.markCompleted(trackingId, dto);
                results.add(dto);
            }

            meterRegistry.counter("fis.batch.submissions").increment();
            meterRegistry.gauge("fis.batch.last.size", drafts.size());
            log.info("Batch processed {} journal entries", drafts.size());

            return results;
        } catch (Exception e) {
            log.error("Batch submission failed for {} messages", messages.size(), e);
            meterRegistry.counter("fis.batch.error").increment();
            for (int i = 0; i < messages.size(); i++) {
                asyncJobStatusService.markFailed(messages.get(i).getTrackingId(),
                        e.getMessage(), e.getClass().getSimpleName());
            }
            throw e;
        } finally {
            sample.stop(Timer.builder("fis.batch.submission.duration").register(meterRegistry));
        }
    }

    private void validateMessage(JournalWriteMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (message.getTenantId() == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        if (message.getRequest() == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (message.getRequest().getLines() == null || message.getRequest().getLines().isEmpty()) {
            throw new IllegalArgumentException("Journal entry must have at least one line");
        }
        if (message.getRequest().getLines().size() > 100) {
            throw new IllegalArgumentException("Journal entry cannot have more than 100 lines");
        }
        for (var line : message.getRequest().getLines()) {
            if (line.getAccountCode() == null || line.getAccountCode().isBlank()) {
                throw new IllegalArgumentException("Line account code cannot be blank");
            }
            if (line.getAmountCents() == null || line.getAmountCents() <= 0) {
                throw new IllegalArgumentException("Line amount must be positive");
            }
        }
    }

    public void submit(JournalWriteMessage message) {
        if (!batchEnabled) {
            processIndividually(List.of(message));
            return;
        }

        shardRouter.getShardForTenant(message.getTenantId());

        BatchBucket bucket = tenantBuckets.computeIfAbsent(message.getTenantId(),
                k -> new BatchBucket(message.getTenantId()));

        synchronized (bucket) {
            bucket.messages.add(message);
            if (bucket.messages.size() >= maxBatchSize) {
                flushBucket(bucket);
            }
        }
    }

    private void flushBucket(BatchBucket bucket) {
        List<JournalWriteMessage> toFlush;
        synchronized (bucket) {
            if (bucket.messages.isEmpty()) {
                return;
            }
            toFlush = new ArrayList<>(bucket.messages);
            bucket.messages.clear();
        }

        try {
            submitBatch(toFlush);
        } catch (Exception e) {
            log.error("Failed to flush bucket for tenant {}", bucket.tenantId, e);
        }
    }

    private void flushAll() {
        if (!running) {
            return;
        }

        for (Map.Entry<UUID, BatchBucket> entry : tenantBuckets.entrySet()) {
            BatchBucket bucket = entry.getValue();
            boolean shouldFlush;
            synchronized (bucket) {
                shouldFlush = !bucket.messages.isEmpty();
            }
            if (shouldFlush) {
                flushBucket(bucket);
            }
        }
    }

    public void shutdown() {
        running = false;
        scheduler.shutdown();
        flushAll();
    }

    private List<JournalEntryResponseDto> processIndividually(List<JournalWriteMessage> messages) {
        List<JournalEntryResponseDto> results = new ArrayList<>();
        for (JournalWriteMessage message : messages) {
            try {
                BusinessEntity tenant = businessEntityRepository.findById(message.getTenantId())
                        .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + message.getTenantId()));

                DraftJournalEntry draft = buildDraft(message.getTenantId(), tenant, message.getRequest());

                shardRouter.getShardForTenant(message.getTenantId());
                var entries = ledgerPersistenceService.persistBatch(List.of(draft));

                JournalEntryResponseDto dto = toResponseDto(entries.getFirst());
                asyncJobStatusService.markCompleted(message.getTrackingId(), dto);
                results.add(dto);
            } catch (Exception e) {
                log.error("Failed to process message {}", message.getTrackingId(), e);
                asyncJobStatusService.markFailed(message.getTrackingId(),
                        e.getMessage(), e.getClass().getSimpleName());
            }
        }
        return results;
    }

    private DraftJournalEntry buildDraft(UUID tenantId, BusinessEntity tenant, CreateJournalEntryRequestDto request) {
        LocalDate effectiveDate = request.getEffectiveDate() != null
                ? request.getEffectiveDate()
                : request.getPostedDate();
        LocalDate transactionDate = request.getTransactionDate() != null
                ? request.getTransactionDate()
                : request.getPostedDate();

        DraftJournalEntry draft = messageMapper.toDraft(request);
        draft.setTenantId(tenantId);
        draft.setBaseCurrency(tenant.getBaseCurrency());
        draft.setEffectiveDate(effectiveDate);
        draft.setTransactionDate(transactionDate);
        return draft;
    }

    private JournalEntryResponseDto toResponseDto(com.bracit.fisprocess.domain.entity.JournalEntry entry) {
        JournalEntryResponseDto response = new JournalEntryResponseDto();
        response.setJournalEntryId(entry.getId());
        response.setLineCount(entry.getLines().size());
        response.setStatus(entry.getStatus());
        return response;
    }

    private static class BatchBucket {
        final UUID tenantId;
        final List<JournalWriteMessage> messages = new ArrayList<>();
        volatile long lastFlushTime;

        BatchBucket(UUID tenantId) {
            this.tenantId = tenantId;
        }
    }
}