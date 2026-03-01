package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
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

    private SimpleMeterRegistry meterRegistry;
    private CircuitBreaker circuitBreaker;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        circuitBreaker = CircuitBreaker.of("rabbitOutboxPublish",
                CircuitBreakerConfig.custom().slidingWindowSize(5).minimumNumberOfCalls(2).build());
        outboxService = new OutboxServiceImpl(
                outboxEventRepository,
                rabbitTemplate,
                jsonMapper,
                meterRegistry,
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
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(outboxEventRepository.countByPublishedFalse()).thenReturn(1L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(2)), Optional.empty());
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
                .createdAt(OffsetDateTime.now().minusMinutes(3))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(outboxEventRepository.countByPublishedFalse()).thenReturn(1L, 1L, 1L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(3)));
        doThrow(new RuntimeException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();

        assertThat(meterRegistry.get("fis.outbox.publish.success.count").counter().count()).isEqualTo(0.0);
        assertThat(meterRegistry.get("fis.outbox.publish.failure.count").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("fis.outbox.oldest.unpublished.age.seconds").gauge().value()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("relayUnpublished should refresh lag gauges when queue is empty")
    void relayUnpublishedShouldRefreshLagGaugesWhenQueueIsEmpty() {
        when(outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of());
        when(outboxEventRepository.countByPublishedFalse()).thenReturn(3L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(4)), Optional.empty());

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
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        when(outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event), List.of(event));
        when(outboxEventRepository.countByPublishedFalse()).thenReturn(1L, 1L, 1L, 0L);
        when(outboxEventRepository.findOldestUnpublishedCreatedAt())
                .thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(1)), Optional.empty());
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("broker unavailable"))
                .doNothing()
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));

        outboxService.relayUnpublished();
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(1.0);

        outboxService.relayUnpublished();
        assertThat(meterRegistry.get("fis.outbox.retry.streak").gauge().value()).isEqualTo(0.0);
        assertThat(event.isPublished()).isTrue();
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
}
