package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.OutboxAlertConfig;
import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxServiceImpl Unit Tests")
class OutboxServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private DeadLetterQueueService deadLetterQueueService;

    private SimpleMeterRegistry meterRegistry;
    private CircuitBreaker circuitBreaker;
    private OutboxAlertConfig alertConfig;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        circuitBreaker = CircuitBreaker.of("rabbitOutboxPublish",
                CircuitBreakerConfig.custom().slidingWindowSize(5).minimumNumberOfCalls(2).build());
        alertConfig = new OutboxAlertConfig();
        alertConfig.setMaxRetries(50);
        alertConfig.getAlert().setRetryStreakWarning(10);
        alertConfig.getAlert().setOldestWarningSeconds(300);
        alertConfig.getAlert().setBacklogWarning(1000);
        alertConfig.getAlert().setDlqCritical(100);

        outboxService = new OutboxServiceImpl(
                outboxEventRepository,
                rabbitTemplate,
                jsonMapper,
                meterRegistry,
                deadLetterQueueService,
                alertConfig,
                circuitBreaker);
        outboxService.initMetrics();
    }

    @Test
    @DisplayName("relayUnpublished should increment success metrics on successful publish")
    void relayUnpublishedShouldIncrementSuccessMetrics() {
        OutboxEvent event = OutboxEvent.builder()
                .outboxId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .eventType("fis.journal.posted")
                .aggregateType("JOURNAL_ENTRY")
                .aggregateId(UUID.randomUUID())
                .payload("{\"x\":1}")
                .published(false)
                .retryCount(0)
                .maxRetries(50)
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.countByPublishedFalseAndDlqFalse()).thenReturn(1L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(2)), Optional.empty());
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterQueueService.dlqSize()).thenReturn(0L);
        doNothing().when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();

        assertThat(meterRegistry.get("fis.outbox.publish.success.count").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.outbox.publish.failure.count").counter().count()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.unpublished.backlog").gauge().value()).isEqualTo(0.0);
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("relayUnpublished should increment failure metrics and retry streak on publish failure")
    void relayUnpublishedShouldIncrementFailureMetricsOnError() {
        OutboxEvent event = OutboxEvent.builder()
                .outboxId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .eventType("fis.journal.posted")
                .aggregateType("JOURNAL_ENTRY")
                .aggregateId(UUID.randomUUID())
                .payload("{\"x\":1}")
                .published(false)
                .retryCount(0)
                .maxRetries(50)
                .createdAt(OffsetDateTime.now().minusMinutes(3))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.countByPublishedFalseAndDlqFalse()).thenReturn(1L, 1L, 1L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(3)));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterQueueService.dlqSize()).thenReturn(0L);
        doThrow(new RuntimeException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();

        assertThat(meterRegistry.get("fis.outbox.publish.success.count").counter().count()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.publish.failure.count").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.outbox.oldest.unpublished.age.seconds").gauge().value()).isGreaterThan(0.0);
        // Event should have its retryCount incremented
        assertThat(event.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("relayUnpublished should refresh lag gauges when queue is empty")
    void relayUnpublishedShouldRefreshLagGaugesWhenQueueIsEmpty() {
        when(outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc()).thenReturn(List.of());
        when(outboxEventRepository.countByPublishedFalseAndDlqFalse()).thenReturn(3L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(4)), Optional.empty());
        when(deadLetterQueueService.dlqSize()).thenReturn(0L);

        outboxService.relayUnpublished();
        outboxService.relayUnpublished();

        assertThat(meterRegistry.get("fis.outbox.unpublished.backlog").gauge().value()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.oldest.unpublished.age.seconds").gauge().value()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(0.0);
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("relayUnpublished should clear retry streak after a subsequent successful publish")
    void relayUnpublishedShouldClearRetryStreakAfterRecovery() {
        OutboxEvent event = OutboxEvent.builder()
                .outboxId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .eventType("fis.journal.posted")
                .aggregateType("JOURNAL_ENTRY")
                .aggregateId(UUID.randomUUID())
                .payload("{\"x\":1}")
                .published(false)
                .retryCount(0)
                .maxRetries(50)
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event), List.of(event));
        when(outboxEventRepository.countByPublishedFalseAndDlqFalse()).thenReturn(1L, 1L, 1L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(1)), Optional.empty());
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterQueueService.dlqSize()).thenReturn(0L);
        doThrow(new RuntimeException("broker unavailable"))
                .doNothing()
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(1.0);

        outboxService.relayUnpublished();
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(0.0);
        assertThat(event.isPublished()).isTrue();
        // First relay saves on failure (retryCount++), second saves on success (published=true)
        verify(outboxEventRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("relayUnpublished should move event to DLQ after max retries exhausted")
    void relayUnpublishedShouldMoveToDlqAfterMaxRetries() {
        OutboxEvent event = OutboxEvent.builder()
                .outboxId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .eventType("fis.journal.posted")
                .aggregateType("JOURNAL_ENTRY")
                .aggregateId(UUID.randomUUID())
                .payload("{\"x\":1}")
                .published(false)
                .retryCount(49)
                .maxRetries(50)
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(outboxEventRepository.countByPublishedFalseAndDlqFalse()).thenReturn(1L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(5)));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterQueueService.dlqSize()).thenReturn(0L);
        doThrow(new RuntimeException("DLQ trigger"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();

        assertThat(event.getRetryCount()).isEqualTo(50);
        verify(deadLetterQueueService).moveToDlq(eq(event.getOutboxId()), any());
    }
}
