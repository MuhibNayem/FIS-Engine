package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.OutboxAlertConfig;
import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import com.bracit.fisprocess.service.OutboxService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transactional outbox writer + relay with dead-letter queue support.
 * <p>
 * Events that fail to publish after exhausting their per-event retry budget
 * are moved to the DLQ for admin intervention.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;
    private final DeadLetterQueueService deadLetterQueueService;
    private final OutboxAlertConfig outboxAlertConfig;
    private final @Qualifier("rabbitOutboxPublishCircuitBreaker") CircuitBreaker rabbitOutboxPublishCircuitBreaker;

    private final AtomicInteger retryStreak = new AtomicInteger(0);
    private final AtomicLong oldestUnpublishedAgeSeconds = new AtomicLong(0L);
    private final AtomicLong unpublishedBacklog = new AtomicLong(0L);
    private final AtomicLong dlqSizeGauge = new AtomicLong(0L);

    private Counter publishSuccessCounter;
    private Counter publishFailureCounter;
    private Counter dlqAutoMoveCounter;

    @PostConstruct
    void initMetrics() {
        publishSuccessCounter = meterRegistry.counter("fis.outbox.publish.success.count");
        publishFailureCounter = meterRegistry.counter("fis.outbox.publish.failure.count");
        dlqAutoMoveCounter = meterRegistry.counter("fis.outbox.dlq.auto.move.count");
        meterRegistry.gauge("fis.outbox.retry.streak", retryStreak);
        meterRegistry.gauge("fis.outbox.oldest.unpublished.age.seconds", oldestUnpublishedAgeSeconds);
        meterRegistry.gauge("fis.outbox.unpublished.backlog", unpublishedBacklog);
        meterRegistry.gauge("fis.outbox.dlq.size", dlqSizeGauge);
    }

    @Override
    @Transactional
    public void recordJournalPosted(
            UUID tenantId, String sourceEventId, JournalEntry journalEntry, @Nullable String traceparent) {
        int maxRetries = outboxAlertConfig.getMaxRetries();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("journalEntryId", journalEntry.getId());
        payload.put("tenantId", tenantId);
        payload.put("sourceEventId", sourceEventId);
        payload.put("status", journalEntry.getStatus().name());
        payload.put("postedDate", journalEntry.getPostedDate());
        payload.put("createdAt", journalEntry.getCreatedAt());

        OutboxEvent event = OutboxEvent.builder()
                .outboxId(UUID.randomUUID())
                .tenantId(tenantId)
                .eventType("fis.journal.posted")
                .aggregateType("JOURNAL_ENTRY")
                .aggregateId(journalEntry.getId())
                .payload(toJson(payload))
                .traceparent(traceparent)
                .published(false)
                .retryCount(0)
                .maxRetries(maxRetries)
                .build();
        outboxEventRepository.save(event);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${fis.outbox.relay-delay-ms:1000}")
    public void relayUnpublished() {
        refreshLagMetrics();
        List<OutboxEvent> unpublished =
                outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc();
        if (unpublished.isEmpty()) {
            retryStreak.set(0);
            return;
        }

        // Apply exponential backoff based on retry streak to prevent overwhelming
        // the system when RabbitMQ recovers from an extended outage.
        int currentStreak = retryStreak.get();
        if (currentStreak > 0) {
            long backoffMs = computeBackoffMs(currentStreak);
            log.debug("Outbox relay backing off for {}ms due to {} consecutive failures",
                    backoffMs, currentStreak);
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        for (OutboxEvent event : unpublished) {
            try {
                Runnable publishCall = CircuitBreaker.decorateRunnable(rabbitOutboxPublishCircuitBreaker, () ->
                        rabbitTemplate.convertAndSend(
                                RabbitMqTopology.DOMAIN_EXCHANGE,
                                event.getEventType(),
                                event.getPayload(),
                                message -> {
                                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                    if (event.getTraceparent() != null && !event.getTraceparent().isBlank()) {
                                        message.getMessageProperties().setHeader("traceparent", event.getTraceparent());
                                    }
                                    return message;
                                }));
                publishCall.run();
                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
                publishSuccessCounter.increment();
                if (retryStreak.get() > 0) {
                    log.info("Outbox relay recovered after {} failed attempt(s)", retryStreak.get());
                }
                retryStreak.set(0);
            } catch (RuntimeException ex) {
                int streak = retryStreak.incrementAndGet();
                publishFailureCounter.increment();

                // Increment per-event retry count and record the error.
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(truncate(ex.getMessage(), 2048));
                outboxEventRepository.save(event);

                // Check if this event has exhausted its retry budget.
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    deadLetterQueueService.moveToDlq(event.getOutboxId(), event.getLastError());
                    dlqAutoMoveCounter.increment();
                    log.error("Outbox event outboxId='{}' exhausted all {} retries and moved to DLQ",
                            event.getOutboxId(), event.getMaxRetries());
                }

                refreshLagMetrics();
                log.warn(
                        "Outbox publish failed for outboxId='{}'; retryCount={}/{} retryStreak={} backlog={} oldestUnpublishedAgeSeconds={}",
                        event.getOutboxId(),
                        event.getRetryCount(),
                        event.getMaxRetries(),
                        streak,
                        unpublishedBacklog.get(),
                        oldestUnpublishedAgeSeconds.get(),
                        ex);

                int alertThreshold = outboxAlertConfig.getAlert().getRetryStreakWarning();
                if (streak >= alertThreshold) {
                    log.error("Outbox retry streak alert threshold reached: streak={} threshold={}",
                            streak, alertThreshold);
                }
                // Break on first failure to preserve ordering and apply backoff.
                break;
            }
        }
        refreshLagMetrics();
    }

    // -- internal helpers ---------------------------------------------------

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unable to serialize outbox payload", e);
        }
    }

    private void refreshLagMetrics() {
        long backlog = outboxEventRepository.countByPublishedFalseAndDlqFalse();
        unpublishedBacklog.set(backlog);
        long dlqSize = deadLetterQueueService.dlqSize();
        dlqSizeGauge.set(dlqSize);

        if (backlog == 0) {
            oldestUnpublishedAgeSeconds.set(0L);
            return;
        }

        OffsetDateTime oldest = outboxEventRepository.findOldestUnpublishedCreatedAt().orElse(null);
        if (oldest == null) {
            oldestUnpublishedAgeSeconds.set(0L);
            return;
        }

        long ageSeconds = Duration.between(oldest, OffsetDateTime.now()).getSeconds();
        oldestUnpublishedAgeSeconds.set(Math.max(ageSeconds, 0L));

        int alertThresholdSec = outboxAlertConfig.getAlert().getOldestWarningSeconds();
        if (oldestUnpublishedAgeSeconds.get() >= alertThresholdSec) {
            log.warn("Outbox oldest unpublished age alert threshold reached: "
                            + "ageSeconds={} thresholdSeconds={} backlog={}",
                    oldestUnpublishedAgeSeconds.get(), alertThresholdSec, backlog);
        }
    }

    /**
     * Computes exponential backoff in milliseconds based on retry streak count.
     * Formula: min(1000 * 2^(streak-1), 30000) — caps at 30 seconds.
     */
    private long computeBackoffMs(int streak) {
        long baseBackoffMs = 1000L;
        long maxBackoffMs = 30_000L;
        long backoff = baseBackoffMs * (1L << Math.min(streak - 1, 5));
        return Math.min(backoff, maxBackoffMs);
    }

    private static String truncate(@Nullable String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }
}
