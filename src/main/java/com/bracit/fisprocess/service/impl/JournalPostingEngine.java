package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.service.ActorRoleResolver;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.MultiCurrencyService;
import com.bracit.fisprocess.service.OutboxService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared posting engine used by direct posting and workflow approval.
 */
@Component
@RequiredArgsConstructor
public class JournalPostingEngine {

    private final JournalEntryValidationService validationService;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final PeriodValidationService periodValidationService;
    private final MultiCurrencyService multiCurrencyService;
    private final ActorRoleResolver actorRoleResolver;
    private final OutboxService outboxService;
    private final ModelMapper modelMapper;

    public JournalEntryResponseDto post(
            UUID tenantId,
            DraftJournalEntry draft,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {
        periodValidationService.validatePostingAllowed(
                tenantId,
                draft.getEffectiveDate(),
                actorRoleResolver.resolve(actorRoleHeader));

        DraftJournalEntry converted = multiCurrencyService.apply(draft);
        validationService.validate(converted);
        JournalEntry persisted = ledgerPersistenceService.persist(converted);
        outboxService.recordJournalPosted(tenantId, converted.getEventId(), persisted, traceparent);
        return toResponseDto(persisted);
    }

    private JournalEntryResponseDto toResponseDto(JournalEntry entry) {
        JournalEntryResponseDto response = modelMapper.map(entry, JournalEntryResponseDto.class);
        response.setJournalEntryId(entry.getId());
        response.setLineCount(entry.getLines().size());
        return response;
    }
}
