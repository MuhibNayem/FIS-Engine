package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.JournalBatchMode;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryBatchResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.exception.ApprovalViolationException;
import com.bracit.fisprocess.exception.DuplicateIdempotencyKeyException;
import com.bracit.fisprocess.exception.JournalEntryNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.JournalWorkflowRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.JournalWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final JournalWorkflowRepository journalWorkflowRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalPostingEngine journalPostingEngine;
    private final JournalWorkflowService journalWorkflowService;
    private final ModelMapper modelMapper;
    @Value("${fis.approval.threshold-cents:9223372036854775807}")
    private long approvalThresholdCents;

    @Override
    @Transactional
    public JournalEntryResponseDto createJournalEntry(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {
        BusinessEntity tenant = resolveTenant(tenantId);

        DraftJournalEntry draft = buildDraft(tenantId, tenant, request);
        if (requiresApproval(draft)) {
            try {
                return journalWorkflowService.createDraft(tenantId, request, traceparent);
            } catch (DataIntegrityViolationException ex) {
                throw mapEventIdConflict(ex, request.getEventId());
            }
        }
        try {
            return journalPostingEngine.post(tenantId, draft, actorRoleHeader, traceparent);
        } catch (DataIntegrityViolationException ex) {
            throw mapEventIdConflict(ex, request.getEventId());
        }
    }

    @Override
    @Transactional
    public JournalEntryBatchResponseDto createJournalEntriesBatch(
            UUID tenantId,
            CreateJournalEntryBatchRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent) {
        BusinessEntity tenant = resolveTenant(tenantId);
        List<CreateJournalEntryRequestDto> entries = request.getEntries();
        validateBatchEventIds(tenantId, entries);

        List<DraftJournalEntry> drafts = entries.stream()
                .map(entry -> buildDraft(tenantId, tenant, entry))
                .toList();
        JournalBatchMode batchMode = request.getBatchMode();
        if (batchMode == JournalBatchMode.POST_NOW) {
            if (drafts.stream().anyMatch(this::requiresApproval)) {
                throw new ApprovalViolationException(
                        "POST_NOW batch rejected because at least one journal entry requires approval.");
            }
            List<JournalEntryResponseDto> postedEntries = new ArrayList<>();
            for (int i = 0; i < drafts.size(); i++) {
                DraftJournalEntry draft = drafts.get(i);
                String eventId = entries.get(i).getEventId();
                try {
                    postedEntries.add(journalPostingEngine.post(tenantId, draft, actorRoleHeader, traceparent));
                } catch (DataIntegrityViolationException ex) {
                    throw mapEventIdConflict(ex, eventId);
                }
            }
            return JournalEntryBatchResponseDto.builder()
                    .batchMode(batchMode)
                    .count(postedEntries.size())
                    .entries(postedEntries)
                    .build();
        }

        List<JournalEntryResponseDto> draftEntries = new ArrayList<>();
        for (CreateJournalEntryRequestDto entry : entries) {
            try {
                draftEntries.add(journalWorkflowService.createDraft(tenantId, entry, traceparent));
            } catch (DataIntegrityViolationException ex) {
                throw mapEventIdConflict(ex, entry.getEventId());
            }
        }
        return JournalEntryBatchResponseDto.builder()
                .batchMode(batchMode)
                .count(draftEntries.size())
                .entries(draftEntries)
                .build();
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
        LocalDate effectiveDate = request.getEffectiveDate() != null
                ? request.getEffectiveDate()
                : request.getPostedDate();
        LocalDate transactionDate = request.getTransactionDate() != null
                ? request.getTransactionDate()
                : request.getPostedDate();

        DraftJournalEntry draft = modelMapper.map(request, DraftJournalEntry.class);
        draft.setTenantId(tenantId);
        draft.setBaseCurrency(tenant.getBaseCurrency());
        draft.setEffectiveDate(effectiveDate);
        draft.setTransactionDate(transactionDate);
        draft.setLines(request.getLines().stream().map(this::toDraftLine).toList());
        return draft;
    }

    private BusinessEntity resolveTenant(UUID tenantId) {
        return businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateBatchEventIds(UUID tenantId, List<CreateJournalEntryRequestDto> entries) {
        Set<String> inBatch = new HashSet<>();
        for (CreateJournalEntryRequestDto entry : entries) {
            if (!inBatch.add(entry.getEventId())) {
                throw new DuplicateIdempotencyKeyException(entry.getEventId());
            }
        }
        List<String> eventIds = entries.stream().map(CreateJournalEntryRequestDto::getEventId).toList();
        List<String> existingPosted = journalEntryRepository.findExistingEventIds(tenantId, eventIds);
        if (!existingPosted.isEmpty()) {
            throw new DuplicateIdempotencyKeyException(existingPosted.getFirst());
        }
        List<String> existingWorkflow = journalWorkflowRepository.findExistingEventIds(tenantId, eventIds);
        if (!existingWorkflow.isEmpty()) {
            throw new DuplicateIdempotencyKeyException(existingWorkflow.getFirst());
        }
    }

    private DraftJournalLine toDraftLine(JournalLineRequestDto dto) {
        DraftJournalLine line = modelMapper.map(dto, DraftJournalLine.class);
        line.setBaseAmountCents(dto.getAmountCents());
        return line;
    }

    private boolean requiresApproval(DraftJournalEntry draft) {
        if (approvalThresholdCents <= 0) {
            return false;
        }
        long totalDebits = draft.getLines().stream()
                .filter(line -> !line.isCredit())
                .mapToLong(DraftJournalLine::getAmountCents)
                .sum();
        return totalDebits >= approvalThresholdCents;
    }

    private JournalEntryResponseDto toResponseDto(JournalEntry entry) {
        JournalEntryResponseDto response = modelMapper.map(entry, JournalEntryResponseDto.class);
        response.setJournalEntryId(entry.getId());
        response.setLineCount(entry.getLines().size());
        return response;
    }

    private RuntimeException mapEventIdConflict(DataIntegrityViolationException ex, String eventId) {
        String message = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        if (message != null && message.toLowerCase().contains("event_id")) {
            return new DuplicateIdempotencyKeyException(eventId);
        }
        return ex;
    }
}
