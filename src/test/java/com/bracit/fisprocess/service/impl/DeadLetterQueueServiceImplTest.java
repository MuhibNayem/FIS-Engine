package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeadLetterQueueServiceImpl Unit Tests")
class DeadLetterQueueServiceImplTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter dlqMoveCounter;
    @Mock private Counter dlqRetryCounter;
    @Mock private Counter dlqDiscardCounter;

    private DeadLetterQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter("fis.outbox.dlq.move.count")).thenReturn(dlqMoveCounter);
        when(meterRegistry.counter("fis.outbox.dlq.retry.count")).thenReturn(dlqRetryCounter);
        when(meterRegistry.counter("fis.outbox.dlq.discard.count")).thenReturn(dlqDiscardCounter);
        service = new DeadLetterQueueServiceImpl(outboxEventRepository, meterRegistry);
        service.initMetrics();
    }

    @Test
    @DisplayName("moveToDlq should update event and increment counter")
    void shouldMoveToDlq() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.markAsDlq(eventId, "error details")).thenReturn(1);

        service.moveToDlq(eventId, "error details");

        verify(dlqMoveCounter).increment();
        verify(outboxEventRepository).markAsDlq(eventId, "error details");
    }

    @Test
    @DisplayName("moveToDlq should truncate error message over 2048 chars")
    void shouldTruncateLongError() {
        UUID eventId = UUID.randomUUID();
        String longError = "x".repeat(3000);
        when(outboxEventRepository.markAsDlq(eq(eventId), any())).thenReturn(1);

        service.moveToDlq(eventId, longError);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventRepository).markAsDlq(eq(eventId), errorCaptor.capture());
        assertThat(errorCaptor.getValue()).hasSize(2048);
    }

    @Test
    @DisplayName("moveToDlq should handle null error")
    void shouldHandleNullError() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.markAsDlq(eventId, null)).thenReturn(1);

        service.moveToDlq(eventId, null);

        verify(outboxEventRepository).markAsDlq(eventId, null);
    }

    @Test
    @DisplayName("moveToDlq should not increment counter when event not found")
    void shouldNotIncrementWhenNotFound() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.markAsDlq(eventId, "err")).thenReturn(0);

        service.moveToDlq(eventId, "err");

        org.mockito.Mockito.verifyNoInteractions(dlqMoveCounter);
    }

    @Test
    @DisplayName("listDlq should return page of DLQ events")
    void shouldListDlq() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<OutboxEvent> page = new PageImpl<>(List.of(), pageable, 0);
        when(outboxEventRepository.findAll((Specification<OutboxEvent>) any(), eq(pageable))).thenReturn(page);

        Page<OutboxEvent> result = service.listDlq(pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("retryFromDlq should return true and increment counter when event exists")
    void shouldRetryFromDlq() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.resetRetryState(eventId)).thenReturn(1);

        boolean result = service.retryFromDlq(eventId);

        assertThat(result).isTrue();
        verify(dlqRetryCounter).increment();
    }

    @Test
    @DisplayName("retryFromDlq should return false when event not found")
    void shouldRetryReturnFalseWhenNotFound() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.resetRetryState(eventId)).thenReturn(0);

        boolean result = service.retryFromDlq(eventId);

        assertThat(result).isFalse();
        org.mockito.Mockito.verifyNoInteractions(dlqRetryCounter);
    }

    @Test
    @DisplayName("discardFromDlq should return true and increment counter when event exists")
    void shouldDiscardFromDlq() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.deleteEventById(eventId)).thenReturn(1);

        boolean result = service.discardFromDlq(eventId);

        assertThat(result).isTrue();
        verify(dlqDiscardCounter).increment();
    }

    @Test
    @DisplayName("discardFromDlq should return false when event not found")
    void shouldDiscardReturnFalseWhenNotFound() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.deleteEventById(eventId)).thenReturn(0);

        boolean result = service.discardFromDlq(eventId);

        assertThat(result).isFalse();
        org.mockito.Mockito.verifyNoInteractions(dlqDiscardCounter);
    }

    @Test
    @DisplayName("dlqSize should return count of DLQ events")
    void shouldReturnDlqSize() {
        when(outboxEventRepository.countByDlqTrue()).thenReturn(5L);

        long size = service.dlqSize();

        assertThat(size).isEqualTo(5L);
    }
}
