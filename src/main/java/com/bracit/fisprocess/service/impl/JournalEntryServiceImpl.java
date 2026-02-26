package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.exception.JournalEntryNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.service.ActorRoleResolver;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.MultiCurrencyService;
import com.bracit.fisprocess.service.OutboxService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrates Journal Entry creation: builds draft → validates → persists →
 * returns response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalEntryServiceImpl implements JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalEntryValidationService validationService;
    private final LedgerPersistenceService ledgerPersistenceService;
    private final PeriodValidationService periodValidationService;
    private final MultiCurrencyService multiCurrencyService;
    private final ActorRoleResolver actorRoleResolver;
    private final OutboxService outboxService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public JournalEntryResponseDto createJournalEntry(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {
        // 1. Resolve tenant
        BusinessEntity tenant = businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        // 2. Validate accounting period by posting date.
        periodValidationService.validatePostingAllowed(
                tenantId,
                request.getPostedDate(),
                actorRoleResolver.resolve(actorRoleHeader));

        // 3. Build draft from request
        DraftJournalEntry draft = buildDraft(tenantId, tenant, request);

        // 4. Apply FX conversion / base amount derivation.
        draft = multiCurrencyService.apply(draft);

        // 5. Validate double-entry rules
        validationService.validate(draft);

        // 6. Persist atomically (hash chain + balance updates)
        JournalEntry persisted = ledgerPersistenceService.persist(draft);

        // 7. Emit immutable domain event via transactional outbox.
        outboxService.recordJournalPosted(tenantId, request.getEventId(), persisted, traceparent);

        // 8. Map to response
        return toResponseDto(persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public JournalEntryResponseDto getJournalEntry(UUID tenantId, UUID journalEntryId) {
        JournalEntry entry = journalEntryRepository.findByTenantIdAndId(tenantId, journalEntryId)
                .orElseThrow(() -> new JournalEntryNotFoundException(journalEntryId));
        return toResponseDto(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JournalEntryResponseDto> listJournalEntries(
            UUID tenantId,
            @Nullable LocalDate postedDateFrom,
            @Nullable LocalDate postedDateTo,
            @Nullable String accountCode,
            @Nullable JournalStatus status,
            @Nullable String referenceId,
            Pageable pageable) {
        return journalEntryRepository.findByTenantIdWithFilters(
                tenantId, postedDateFrom, postedDateTo, accountCode, status, referenceId, pageable)
                .map(this::toResponseDto);
    }

    private DraftJournalEntry buildDraft(UUID tenantId, BusinessEntity tenant,
            CreateJournalEntryRequestDto request) {
        DraftJournalEntry draft = modelMapper.map(request, DraftJournalEntry.class);
        draft.setTenantId(tenantId);
        draft.setBaseCurrency(tenant.getBaseCurrency());
        draft.setLines(request.getLines().stream().map(this::toDraftLine).toList());
        return draft;
    }

    private DraftJournalLine toDraftLine(JournalLineRequestDto dto) {
        DraftJournalLine line = modelMapper.map(dto, DraftJournalLine.class);
        line.setBaseAmountCents(dto.getAmountCents());
        return line;
    }

    private JournalEntryResponseDto toResponseDto(JournalEntry entry) {
        JournalEntryResponseDto response = modelMapper.map(entry, JournalEntryResponseDto.class);
        response.setJournalEntryId(entry.getId());
        response.setLineCount(entry.getLines().size());
        return response;
    }
}
