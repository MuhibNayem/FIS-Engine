package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryBatchResponseDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrator service for Journal Entry operations.
 */
public interface JournalEntryService {

    default JournalEntryResponseDto createJournalEntry(UUID tenantId, CreateJournalEntryRequestDto request) {
        return createJournalEntry(tenantId, request, null, null);
    }

    default JournalEntryResponseDto createJournalEntry(
            UUID tenantId, CreateJournalEntryRequestDto request, @Nullable String actorRoleHeader) {
        return createJournalEntry(tenantId, request, actorRoleHeader, null);
    }

    JournalEntryResponseDto createJournalEntry(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent);

    JournalEntryBatchResponseDto createJournalEntriesBatch(
            UUID tenantId,
            CreateJournalEntryBatchRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent);

    JournalEntryResponseDto getJournalEntry(UUID tenantId, UUID journalEntryId);

    Page<JournalEntryResponseDto> listJournalEntries(
            UUID tenantId,
            @Nullable LocalDate postedDateFrom,
            @Nullable LocalDate postedDateTo,
            @Nullable String accountCode,
            @Nullable JournalStatus status,
            @Nullable String referenceId,
            Pageable pageable);
}
