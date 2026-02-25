package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
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

    JournalEntryResponseDto createJournalEntry(UUID tenantId, CreateJournalEntryRequestDto request);

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
