package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.request.IngestionEnvelopeDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.exception.FisBusinessException;
import com.bracit.fisprocess.service.IdempotentLedgerWriteService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.RuleMappingService;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventIngestionConsumer Unit Tests")
class EventIngestionConsumerTest {

        @Mock
        private JournalEntryService journalEntryService;
        @Mock
        private IdempotentLedgerWriteService idempotentLedgerWriteService;
        @Mock
        private RuleMappingService ruleMappingService;
        @Mock
        private Validator validator;
        @Mock
        private ModelMapper modelMapper;
        @Mock
        private Channel channel;

        @InjectMocks
        private EventIngestionConsumer consumer;

        private final UUID tenantId = UUID.randomUUID();
        private final long deliveryTag = 42L;

        @Test
        @DisplayName("should reject malformed messages")
        void shouldRejectMalformedMessages() throws IOException {
                consumer.consume(null, channel, deliveryTag);
                verify(channel).basicReject(deliveryTag, false);
        }

        @Test
        @DisplayName("should ack duplicate events through idempotent wrapper")
        void shouldAckDuplicateEvents() throws IOException {
                FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                                .eventId("evt-dup")
                                .eventType("PAYMENT")
                                .createdBy("system")
                                .build();
                IngestionEnvelopeDto envelope = IngestionEnvelopeDto.builder()
                                .tenantId(tenantId)
                                .event(event)
                                .build();

                when(validator.validate(event)).thenReturn(Collections.emptySet());
                when(idempotentLedgerWriteService.execute(
                                eq(tenantId), eq("evt-dup"), any(), eq(EventIngestionResponseDto.class), any(), eq(true)))
                                .thenReturn(EventIngestionResponseDto.builder()
                                                .status("ACCEPTED")
                                                .ik("evt-dup")
                                                .message("Event queued for ledger processing.")
                                                .build());

                consumer.consume(envelope, channel, deliveryTag);

                verify(channel).basicAck(deliveryTag, false);
                verify(journalEntryService, never()).createJournalEntry(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should process new event and ack")
        void shouldProcessNewEventAndAck() throws IOException {
                FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                                .eventId("evt-new")
                                .eventType("PAYMENT")
                                .createdBy("system")
                                .build();
                IngestionEnvelopeDto envelope = IngestionEnvelopeDto.builder()
                                .tenantId(tenantId)
                                .event(event)
                                .build();

                DraftJournalEntry draft = DraftJournalEntry.builder()
                                .tenantId(tenantId)
                                .eventId("evt-new")
                                .lines(List.of(DraftJournalLine.builder()
                                                .accountCode("CASH")
                                                .amountCents(1000L)
                                                .isCredit(false)
                                                .build()))
                                .build();

                when(validator.validate(event)).thenReturn(Collections.emptySet());
                when(ruleMappingService.mapToDraft(eq(tenantId), eq(event), eq("system"))).thenReturn(draft);
                when(modelMapper.map(any(DraftJournalEntry.class), eq(CreateJournalEntryRequestDto.class)))
                                .thenReturn(CreateJournalEntryRequestDto.builder()
                                                .eventId("evt-new")
                                                .lines(new java.util.ArrayList<>())
                                                .build());
                when(idempotentLedgerWriteService.execute(
                                eq(tenantId), eq("evt-new"), any(), eq(EventIngestionResponseDto.class), any(), eq(true)))
                                .thenAnswer(invocation -> {
                                        @SuppressWarnings("unchecked")
                                        Supplier<EventIngestionResponseDto> supplier = invocation.getArgument(4);
                                        return supplier.get();
                                });

                consumer.consume(envelope, channel, deliveryTag);

                verify(journalEntryService).createJournalEntry(eq(tenantId), any(), any(), any());
                verify(channel).basicAck(deliveryTag, false);
        }

        @Test
        @DisplayName("should nack transient failures for requeue")
        void shouldNackTransientFailures() throws IOException {
                FinancialEventRequestDto event = FinancialEventRequestDto.builder()
                                .eventId("evt-transient")
                                .eventType("PAYMENT")
                                .createdBy("system")
                                .build();
                IngestionEnvelopeDto envelope = IngestionEnvelopeDto.builder()
                                .tenantId(tenantId)
                                .event(event)
                                .build();

                when(validator.validate(event)).thenReturn(Collections.emptySet());
                when(idempotentLedgerWriteService.execute(
                                eq(tenantId), eq("evt-transient"), any(), eq(EventIngestionResponseDto.class), any(), eq(true)))
                                .thenThrow(new RuntimeException("DB connection lost"));

                consumer.consume(envelope, channel, deliveryTag);

                verify(channel).basicNack(deliveryTag, false, true);
        }
}
