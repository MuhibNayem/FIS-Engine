package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transactional outbox writer + relay.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;
    private final @Qualifier("rabbitOutboxPublishCircuitBreaker") CircuitBreaker rabbitOutboxPublishCircuitBreaker;
    private final AtomicInteger retryStreak = new AtomicInteger(0);
    private final AtomicLong oldestUnpublishedAgeSeconds = new AtomicLong(0L);
    private final AtomicLong unpublishedBacklog = new AtomicLong(0L);
    @Value("${fis.outbox.alert.retry-streak-threshold:5}")
    private int retryStreakAlertThreshold;
    @Value("${fis.outbox.alert.oldest-unpublished-seconds:300}")
    private long oldestUnpublishedAlertThresholdSeconds;
    private Counter publishSuccessCounter;
    private Counter publishFailureCounter;

    @PostConstruct
    void initMetrics() {
        publishSuccessCounter = meterRegistry.counter("fis.outbox.publish.success.count");
        publishFailureCounter = meterRegistry.counter("fis.outbox.publish.failure.count");
        meterRegistry.gauge("fis.outbox.retry.streak", retryStreak);
        meterRegistry.gauge("fis.outbox.oldest.unpublished.age.seconds", oldestUnpublishedAgeSeconds);
        meterRegistry.gauge("fis.outbox.unpublished.backlog", unpublishedBacklog);
    }

    @Override
    @Transactional
    public void recordJournalPosted(
            UUID tenantId, String sourceEventId, JournalEntry journalEntry, @Nullable String traceparent) {
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
                .build();
        outboxEventRepository.save(event);
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${fis.outbox.relay-delay-ms:1000}")
    public void relayUnpublished() {
        refreshLagMetrics();
        List<OutboxEvent> unpublished = outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (unpublished.isEmpty()) {
            retryStreak.set(0);
            return;
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
                refreshLagMetrics();
                log.warn(
                        "Outbox publish failed for outboxId='{}'; retryStreak={} backlog={} oldestUnpublishedAgeSeconds={}",
                        event.getOutboxId(),
                        streak,
                        unpublishedBacklog.get(),
                        oldestUnpublishedAgeSeconds.get(),
                        ex);
                if (streak >= retryStreakAlertThreshold) {
                    log.error("Outbox retry streak alert threshold reached: streak={} threshold={}",
                            streak, retryStreakAlertThreshold);
                }
                break;
            }
        }
        refreshLagMetrics();
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unable to serialize outbox payload", e);
        }
    }

    private void refreshLagMetrics() {
        long backlog = outboxEventRepository.countByPublishedFalse();
        unpublishedBacklog.set(backlog);
        if (backlog == 0) {
            oldestUnpublishedAgeSeconds.set(0L);
            return;
        }

        OffsetDateTime oldest = outboxEventRepository.findOldestUnpublishedCreatedAt().orElse(null);
        if (oldest == null) {
            oldestUnpublishedAgeSeconds.set(0L);
            return;
        }

        long ageSeconds = java.time.Duration.between(oldest, OffsetDateTime.now()).getSeconds();
        oldestUnpublishedAgeSeconds.set(Math.max(ageSeconds, 0L));
        if (oldestUnpublishedAgeSeconds.get() >= oldestUnpublishedAlertThresholdSeconds) {
            log.warn("Outbox oldest unpublished age alert threshold reached: ageSeconds={} thresholdSeconds={} backlog={}",
                    oldestUnpublishedAgeSeconds.get(), oldestUnpublishedAlertThresholdSeconds, backlog);
        }
    }
}
