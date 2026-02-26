package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.CorrectionRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.ReversalRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.ReversalResponseDto;
import com.bracit.fisprocess.exception.InvalidReversalException;
import com.bracit.fisprocess.exception.JournalEntryNotFoundException;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.IdempotentLedgerWriteService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.JournalReversalService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JournalReversalServiceImpl implements JournalReversalService {

    private final JournalEntryRepository journalEntryRepository;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final OutboxService outboxService;
    private final JournalEntryService journalEntryService;
    private final IdempotentLedgerWriteService idempotentLedgerWriteService;

    @Override
    public ReversalResponseDto reverse(UUID tenantId, UUID originalJournalEntryId, ReversalRequestDto request) {
        return idempotentLedgerWriteService.execute(
                tenantId,
                request.getEventId(),
                Map.of("operation", "reverse", "journalEntryId", originalJournalEntryId, "reason", request.getReason()),
                ReversalResponseDto.class,
                () -> performReverse(tenantId, originalJournalEntryId, request));
    }

    @Override
    public ReversalResponseDto correct(UUID tenantId, UUID originalJournalEntryId, CorrectionRequestDto request) {
        return idempotentLedgerWriteService.execute(
                tenantId,
                request.getEventId(),
                Map.of(
                        "operation", "correct",
                        "journalEntryId", originalJournalEntryId,
                        "reversalEventId", request.getReversalEventId(),
                        "replacementEventId", request.getEventId()),
                ReversalResponseDto.class,
                () -> performCorrection(tenantId, originalJournalEntryId, request));
    }

    @Transactional
    protected ReversalResponseDto performReverse(UUID tenantId, UUID originalJournalEntryId, ReversalRequestDto request) {
        JournalEntry original = journalEntryRepository.findWithLinesByTenantIdAndId(tenantId, originalJournalEntryId)
                .orElseThrow(() -> new JournalEntryNotFoundException(originalJournalEntryId));

        if (journalEntryRepository.existsByReversalOfId(original.getId())) {
            throw new InvalidReversalException(
                    "Journal Entry '" + original.getId() + "' already has a posted reversal entry.");
        }

        DraftJournalEntry mirrorDraft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .eventId(request.getEventId())
                .postedDate(original.getPostedDate())
                .description("Reversal of " + original.getId() + ": " + request.getReason())
                .referenceId(original.getReferenceId())
                .transactionCurrency(original.getTransactionCurrency())
                .baseCurrency(original.getBaseCurrency())
                .exchangeRate(original.getExchangeRate())
                .createdBy(request.getCreatedBy())
                .reversalOfId(original.getId())
                .lines(original.getLines().stream()
                        .map(line -> DraftJournalLine.builder()
                                .accountCode(line.getAccount().getCode())
                                .amountCents(line.getAmount())
                                .baseAmountCents(line.getBaseAmount())
                                .isCredit(!line.isCredit())
                                .dimensions(line.getDimensions())
                                .build())
                        .toList())
                .build();

        JournalEntry reversal = ledgerPersistenceService.persist(mirrorDraft);
        outboxService.recordJournalPosted(tenantId, request.getEventId(), reversal, null);

        return ReversalResponseDto.builder()
                .reversalJournalEntryId(reversal.getId())
                .originalJournalEntryId(original.getId())
                .status(reversal.getStatus().name())
                .message("Reversal entry posted. Original entry remains immutable.")
                .build();
    }

    @Transactional
    protected ReversalResponseDto performCorrection(UUID tenantId, UUID originalJournalEntryId, CorrectionRequestDto request) {
        ReversalResponseDto reversalResponse = performReverse(
                tenantId,
                originalJournalEntryId,
                ReversalRequestDto.builder()
                        .eventId(request.getReversalEventId())
                        .reason("Correction for " + originalJournalEntryId)
                        .createdBy(request.getCreatedBy())
                        .build());

        JournalEntryResponseDto replacement = journalEntryService.createJournalEntry(
                tenantId,
                CreateJournalEntryRequestDto.builder()
                        .eventId(request.getEventId())
                        .postedDate(request.getPostedDate())
                        .description(request.getDescription())
                        .referenceId(request.getReferenceId())
                        .transactionCurrency(request.getTransactionCurrency())
                        .createdBy(request.getCreatedBy())
                        .lines(copyLines(request.getLines()))
                        .build());

        return ReversalResponseDto.builder()
                .reversalJournalEntryId(reversalResponse.getReversalJournalEntryId())
                .originalJournalEntryId(originalJournalEntryId)
                .status(replacement.getStatus().name())
                .message("Correction posted. Reversal JE=" + reversalResponse.getReversalJournalEntryId()
                        + ", replacement JE=" + replacement.getJournalEntryId())
                .build();
    }

    private List<JournalLineRequestDto> copyLines(List<JournalLineRequestDto> lines) {
        return lines.stream().map(line -> JournalLineRequestDto.builder()
                .accountCode(line.getAccountCode())
                .amountCents(line.getAmountCents())
                .isCredit(line.isCredit())
                .dimensions(line.getDimensions())
                .build()).toList();
    }
}
