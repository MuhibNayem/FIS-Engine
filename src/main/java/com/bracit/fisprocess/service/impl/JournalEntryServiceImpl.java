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
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.JournalEntryValidationService;
import com.bracit.fisprocess.service.LedgerPersistenceService;
import com.bracit.fisprocess.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
    private final OutboxService outboxService;

    @Override
    @Transactional
    public JournalEntryResponseDto createJournalEntry(UUID tenantId, CreateJournalEntryRequestDto request) {
        // 1. Resolve tenant
        BusinessEntity tenant = businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        // 2. Build draft from request
        DraftJournalEntry draft = buildDraft(tenantId, tenant, request);

        // 3. Validate double-entry rules
        validationService.validate(draft);

        // 4. Persist atomically (hash chain + balance updates)
        JournalEntry persisted = ledgerPersistenceService.persist(draft);

        // 5. Emit immutable domain event via transactional outbox.
        outboxService.recordJournalPosted(tenantId, request.getEventId(), persisted);

        // 6. Map to response
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
        return DraftJournalEntry.builder()
                .tenantId(tenantId)
                .eventId(request.getEventId())
                .postedDate(request.getPostedDate())
                .description(request.getDescription())
                .referenceId(request.getReferenceId())
                .transactionCurrency(request.getTransactionCurrency())
                .baseCurrency(tenant.getBaseCurrency())
                .createdBy(request.getCreatedBy())
                .lines(request.getLines().stream()
                        .map(this::toDraftLine)
                        .toList())
                .build();
    }

    private DraftJournalLine toDraftLine(JournalLineRequestDto dto) {
        return DraftJournalLine.builder()
                .accountCode(dto.getAccountCode())
                .amountCents(dto.getAmountCents())
                .baseAmountCents(dto.getAmountCents()) // same-currency for Phase 2; multi-currency in Phase 4
                .isCredit(dto.isCredit())
                .dimensions(dto.getDimensions())
                .build();
    }

    private JournalEntryResponseDto toResponseDto(JournalEntry entry) {
        return JournalEntryResponseDto.builder()
                .journalEntryId(entry.getId())
                .postedDate(entry.getPostedDate())
                .status(entry.getStatus())
                .description(entry.getDescription())
                .referenceId(entry.getReferenceId())
                .transactionCurrency(entry.getTransactionCurrency())
                .baseCurrency(entry.getBaseCurrency())
                .exchangeRate(entry.getExchangeRate())
                .lineCount(entry.getLines().size())
                .reversalOfId(entry.getReversalOfId())
                .createdBy(entry.getCreatedBy())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
