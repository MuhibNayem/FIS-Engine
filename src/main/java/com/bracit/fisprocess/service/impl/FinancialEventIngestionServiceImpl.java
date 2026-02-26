package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.IngestionEnvelopeDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.exception.DuplicateIdempotencyKeyException;
import com.bracit.fisprocess.service.FinancialEventIngestionService;
import com.bracit.fisprocess.service.IdempotencyService;
import com.bracit.fisprocess.service.PayloadHashService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * REST ingestion service that publishes incoming events to RabbitMQ.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialEventIngestionServiceImpl implements FinancialEventIngestionService {

    private final PayloadHashService payloadHashService;
    private final IdempotencyService idempotencyService;
    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public EventIngestionResponseDto ingest(
            UUID tenantId, String sourceSystem, FinancialEventRequestDto request, String traceparent) {
        String payloadHash = payloadHashService.sha256Hex(request);
        IdempotencyService.IdempotencyCheckResult result = idempotencyService
                .checkAndMarkProcessing(tenantId, request.getEventId(), payloadHash);

        if (result.state() == IdempotencyService.IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD) {
            throw new DuplicateIdempotencyKeyException(request.getEventId());
        }

        if (result.state() == IdempotencyService.IdempotencyState.DUPLICATE_SAME_PAYLOAD) {
            EventIngestionResponseDto cachedResponse = fromCachedResponse(result.cachedResponse());
            if (cachedResponse != null) {
                return cachedResponse;
            }
            return EventIngestionResponseDto.builder()
                    .status("ACCEPTED")
                    .ik(request.getEventId())
                    .message("Duplicate event acknowledged (idempotent).")
                    .build();
        }

        IngestionEnvelopeDto envelope = IngestionEnvelopeDto.builder()
                .tenantId(tenantId)
                .sourceSystem(sourceSystem)
                .payloadHash(payloadHash)
                .traceparent(traceparent)
                .event(request)
                .build();

        String routingKey = sourceSystem.toLowerCase() + "." + request.getEventType().toLowerCase() + ".v1";
        try {
            rabbitTemplate.convertAndSend(RabbitMqTopology.EVENTS_EXCHANGE, routingKey, envelope, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                if (traceparent != null && !traceparent.isBlank()) {
                    message.getMessageProperties().setHeader("traceparent", traceparent);
                }
                return message;
            });
        } catch (RuntimeException ex) {
            idempotencyService.markFailed(tenantId, request.getEventId(), payloadHash, "Queue publish failed");
            throw ex;
        }

        log.info("Accepted event '{}' for tenant '{}' and published to RabbitMQ", request.getEventId(), tenantId);
        return EventIngestionResponseDto.builder()
                .status("ACCEPTED")
                .ik(request.getEventId())
                .message("Event queued for ledger processing.")
                .build();
    }

    private EventIngestionResponseDto fromCachedResponse(String cachedResponse) {
        if (cachedResponse == null || cachedResponse.isBlank() || "{}".equals(cachedResponse)) {
            return null;
        }
        try {
            return jsonMapper.readValue(cachedResponse, EventIngestionResponseDto.class);
        } catch (RuntimeException ex) {
            log.debug("Unable to parse cached idempotency response body", ex);
            return null;
        }
    }
}
