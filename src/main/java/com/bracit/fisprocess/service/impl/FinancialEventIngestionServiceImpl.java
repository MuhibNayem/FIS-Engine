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
import org.springframework.stereotype.Service;

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

    @Override
    public EventIngestionResponseDto ingest(UUID tenantId, String sourceSystem, FinancialEventRequestDto request) {
        String payloadHash = payloadHashService.sha256Hex(request);
        IdempotencyService.IdempotencyCheckResult result = idempotencyService
                .checkAndMarkProcessing(tenantId, request.getEventId(), payloadHash);

        if (result.state() == IdempotencyService.IdempotencyState.DUPLICATE_DIFFERENT_PAYLOAD) {
            throw new DuplicateIdempotencyKeyException(request.getEventId());
        }

        if (result.state() == IdempotencyService.IdempotencyState.DUPLICATE_SAME_PAYLOAD) {
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
                .event(request)
                .build();

        String routingKey = sourceSystem.toLowerCase() + "." + request.getEventType().toLowerCase() + ".v1";
        try {
            rabbitTemplate.convertAndSend(RabbitMqTopology.EVENTS_EXCHANGE, routingKey, envelope);
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
}
