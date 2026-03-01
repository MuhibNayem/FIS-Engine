package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.IngestionEnvelopeDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.exception.FisBusinessException;
import com.bracit.fisprocess.service.IdempotentLedgerWriteService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.RuleMappingService;
import com.rabbitmq.client.Channel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Consumes financial events from RabbitMQ and posts journal entries with
 * explicit manual ack.
 * <p>
 * Uses {@link IdempotentLedgerWriteService} for eventId idempotency and
 * delegates posting to {@link JournalEntryService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventIngestionConsumer {

    private final JournalEntryService journalEntryService;
    private final IdempotentLedgerWriteService idempotentLedgerWriteService;
    private final RuleMappingService ruleMappingService;
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
        Set<ConstraintViolation<FinancialEventRequestDto>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            log.warn("Rejecting structurally invalid event payload: {}", violations);
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            EventIngestionResponseDto response = idempotentLedgerWriteService.execute(
                    envelope.getTenantId(),
                    event.getEventId(),
                    event,
                    EventIngestionResponseDto.class,
                    () -> {
                        DraftJournalEntry draft = ruleMappingService.mapToDraft(
                                envelope.getTenantId(), event, event.getCreatedBy());
                        journalEntryService.createJournalEntry(
                                envelope.getTenantId(),
                                toJournalEntryRequest(draft),
                                null,
                                envelope.getTraceparent());
                        return EventIngestionResponseDto.builder()
                                .status("ACCEPTED")
                                .ik(event.getEventId())
                                .message("Event queued for ledger processing.")
                                .build();
                    },
                    true);
            log.debug("Processed/acknowledged event '{}' with status '{}'", event.getEventId(), response.getStatus());
            channel.basicAck(deliveryTag, false);
        } catch (FisBusinessException ex) {
            log.warn("Rejecting business-invalid eventId='{}' tenantId='{}': {}",
                    event.getEventId(), envelope.getTenantId(), ex.getMessage());
            channel.basicReject(deliveryTag, false);
        } catch (RuntimeException ex) {
            // Transient/runtime failures are re-queued.
            log.warn("NACKing eventId='{}' tenantId='{}' for retry",
                    event.getEventId(), envelope.getTenantId(), ex);
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

}
