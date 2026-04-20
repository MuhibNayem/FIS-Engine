package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.exception.DuplicateIdempotencyKeyException;
import com.bracit.fisprocess.service.IdempotencyService;
import com.bracit.fisprocess.service.PayloadHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FinancialEventIngestionServiceImpl Unit Tests")
class FinancialEventIngestionServiceImplTest {

    @Mock private PayloadHashService payloadHashService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RabbitTemplate rabbitTemplate;

    private JsonMapper jsonMapper;
    private FinancialEventIngestionServiceImpl service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String SOURCE = "ERP-SYSTEM";
    private static final String PAYLOAD_HASH = "hash-ingestion-001";

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new FinancialEventIngestionServiceImpl(
                payloadHashService, idempotencyService, rabbitTemplate, jsonMapper);
    }

    private FinancialEventRequestDto buildEvent() {
        return FinancialEventRequestDto.builder()
                .eventId("EVT-INGEST-001")
                .eventType("SALE")
                .build();
    }

    private IdempotencyService.IdempotencyCheckResult newResult() {
        return new IdempotencyService.IdempotencyCheckResult(
                IdempotencyService.IdempotencyState.NEW, null);
    }

    private IdempotencyService.IdempotencyCheckResult duplicateSame(String cachedResponse) {
        return new IdempotencyService.IdempotencyCheckResult(
                IdempotencyService.IdempotencyState.DUPLICATE_SAME_PAYLOAD, cachedResponse);
    }

    private IdempotencyService.IdempotencyCheckResult duplicateDifferent() {
        return new IdempotencyService.IdempotencyCheckResult(
                IdempotencyService.IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD, null);
    }

    @Nested
    @DisplayName("ingest - happy path")
    class IngestHappyPath {

        @Test
        @DisplayName("should accept new event and publish to RabbitMQ")
        void shouldAcceptNewEvent() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(newResult());

            EventIngestionResponseDto result = service.ingest(TENANT_ID, SOURCE, event, "trace-001");

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getIk()).isEqualTo("EVT-INGEST-001");
            assertThat(result.getMessage()).contains("queued");

            ArgumentCaptor<String> exchangeCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(rabbitTemplate).convertAndSend(
                    eq(RabbitMqTopology.EVENTS_EXCHANGE),
                    eq("erp-system.sale.v1"),
                    any(),
                    any(MessagePostProcessor.class));
        }

        @Test
        @DisplayName("should propagate traceparent header to RabbitMQ message")
        void shouldPropagateTraceparent() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(newResult());

            service.ingest(TENANT_ID, SOURCE, event, "00-trace-span-01");

            ArgumentCaptor<MessagePostProcessor> mppCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
            verify(rabbitTemplate).convertAndSend(
                    any(String.class), any(String.class), any(), mppCaptor.capture());

            Message message = new Message("test".getBytes());
            mppCaptor.getValue().postProcessMessage(message);
            assertThat((String) message.getMessageProperties().getHeader("traceparent"))
                    .isEqualTo("00-trace-span-01");
        }
    }

    @Nested
    @DisplayName("ingest - idempotency paths")
    class IngestIdempotency {

        @Test
        @DisplayName("should return ACCEPTED for DUPLICATE_SAME_PAYLOAD with cached response")
        void shouldReturnCachedDuplicate() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            EventIngestionResponseDto cached = EventIngestionResponseDto.builder()
                    .status("ACCEPTED").ik("EVT-INGEST-001").message("original").build();
            String cachedJson = "{\"status\":\"ACCEPTED\",\"ik\":\"EVT-INGEST-001\",\"message\":\"original\"}";
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(duplicateSame(cachedJson));

            EventIngestionResponseDto result = service.ingest(TENANT_ID, SOURCE, event, null);

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getIk()).isEqualTo("EVT-INGEST-001");
        }

        @Test
        @DisplayName("should return ACCEPTED for DUPLICATE_SAME_PAYLOAD with empty cache")
        void shouldReturnDuplicateWithEmptyCache() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(duplicateSame(""));

            EventIngestionResponseDto result = service.ingest(TENANT_ID, SOURCE, event, null);

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getMessage()).contains("idempotent");
        }

        @Test
        @DisplayName("should return ACCEPTED for DUPLICATE_SAME_PAYLOAD with '{}' cache")
        void shouldReturnDuplicateWithEmptyObjectCache() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(duplicateSame("{}"));

            EventIngestionResponseDto result = service.ingest(TENANT_ID, SOURCE, event, null);

            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("should throw DuplicateIdempotencyKeyException for DUPLICATE_DIFFERENT_PAYLOAD")
        void shouldThrowForDifferentPayload() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(duplicateDifferent());

            assertThatThrownBy(() -> service.ingest(TENANT_ID, SOURCE, event, null))
                    .isInstanceOf(DuplicateIdempotencyKeyException.class);
        }
    }

    @Nested
    @DisplayName("ingest - error paths")
    class IngestErrorPaths {

        @Test
        @DisplayName("should mark failed and rethrow when RabbitMQ publish fails")
        void shouldMarkFailedOnRabbitMqFailure() {
            FinancialEventRequestDto event = buildEvent();
            when(payloadHashService.sha256Hex(event)).thenReturn(PAYLOAD_HASH);
            when(idempotencyService.checkAndMarkProcessing(TENANT_ID, "EVT-INGEST-001", PAYLOAD_HASH))
                    .thenReturn(newResult());
            doThrow(new RuntimeException("RabbitMQ connection lost"))
                    .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(), any(MessagePostProcessor.class));

            assertThatThrownBy(() -> service.ingest(TENANT_ID, SOURCE, event, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("RabbitMQ connection lost");

            verify(idempotencyService).markFailed(eq(TENANT_ID), eq("EVT-INGEST-001"), eq(PAYLOAD_HASH), any());
        }
    }
}
