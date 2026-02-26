package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.IngestionEnvelopeDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.exception.FisBusinessException;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.IdempotencyService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.RuleMappingService;
import com.rabbitmq.client.Channel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Set;

/**
 * Consumes financial events and posts journal entries with explicit manual ack.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionConsumer {

    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final IdempotencyService idempotencyService;
    private final RuleMappingService ruleMappingService;
    private final JsonMapper jsonMapper;
    private final Validator validator;
    private final ModelMapper modelMapper;

    @RabbitListener(queues = RabbitMqTopology.INGESTION_QUEUE)
    public void consume(IngestionEnvelopeDto envelope, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (envelope == null || envelope.getTenantId() == null || envelope.getEvent() == null) {
            log.error("Rejecting malformed ingestion message: {}", envelope);
            channel.basicReject(deliveryTag, false);
            return;
        }

        FinancialEventRequestDto event = envelope.getEvent();
        String payloadHash = safePayloadHash(envelope.getPayloadHash());
        Set<ConstraintViolation<FinancialEventRequestDto>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            log.warn("Rejecting structurally invalid event payload: {}", violations);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            if (journalEntryRepository.existsByTenantIdAndEventId(envelope.getTenantId(), event.getEventId())) {
                idempotencyService.markCompleted(
                        envelope.getTenantId(),
                        event.getEventId(),
                        payloadHash,
                        toAcceptedResponseJson(event.getEventId(), "Duplicate event acknowledged (idempotent)."));
                channel.basicAck(deliveryTag, false);
                return;
            }

            DraftJournalEntry draft = ruleMappingService.mapToDraft(envelope.getTenantId(), event, event.getCreatedBy());
            journalEntryService.createJournalEntry(
                    envelope.getTenantId(),
                    toJournalEntryRequest(draft),
                    null,
                    envelope.getTraceparent());

            idempotencyService.markCompleted(
                    envelope.getTenantId(),
                    event.getEventId(),
                    payloadHash,
                    toAcceptedResponseJson(event.getEventId(), "Event queued for ledger processing."));
            channel.basicAck(deliveryTag, false);
        } catch (FisBusinessException ex) {
            log.warn("Rejecting business-invalid eventId='{}' tenantId='{}': {}",
                    event.getEventId(), envelope.getTenantId(), ex.getMessage());
            idempotencyService.markFailed(envelope.getTenantId(), event.getEventId(), payloadHash, toFailureBody(ex));
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException ex) {
            // Transient/runtime failures are re-queued; state remains PROCESSING.
            log.warn("NACKing eventId='{}' tenantId='{}' for retry", event.getEventId(), envelope.getTenantId(), ex);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private CreateJournalEntryRequestDto toJournalEntryRequest(DraftJournalEntry draft) {
        CreateJournalEntryRequestDto dto = modelMapper.map(draft, CreateJournalEntryRequestDto.class);
        dto.setLines(draft.getLines().stream()
                .map(line -> modelMapper.map(line, JournalLineRequestDto.class))
                .toList());
        return dto;
    }

    private String toFailureBody(FisBusinessException ex) {
        try {
            return jsonMapper.writeValueAsString(new FailurePayload(ex.getClass().getSimpleName(), ex.getMessage()));
        } catch (RuntimeException jsonException) {
            return "{\"error\":\"BUSINESS_FAILURE\"}";
        }
    }

    private String toAcceptedResponseJson(String eventId, String message) {
        try {
            return jsonMapper.writeValueAsString(EventIngestionResponseDto.builder()
                    .status("ACCEPTED")
                    .ik(eventId)
                    .message(message)
                    .build());
        } catch (RuntimeException jsonException) {
            return "{\"status\":\"ACCEPTED\",\"ik\":\"" + eventId + "\"}";
        }
    }

    private String safePayloadHash(@Nullable String payloadHash) {
        return payloadHash == null || payloadHash.isBlank() ? "unknown" : payloadHash;
    }

    private record FailurePayload(String error, String message) {
    }
}
