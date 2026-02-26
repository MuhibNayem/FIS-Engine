package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        List<OutboxEvent> unpublished = outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : unpublished) {
            try {
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
                        });
                event.setPublished(true);
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            } catch (RuntimeException ex) {
                log.warn("Outbox publish failed for outboxId='{}'; will retry", event.getOutboxId(), ex);
                break;
            }
        }
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Unable to serialize outbox payload", e);
        }
    }
}
