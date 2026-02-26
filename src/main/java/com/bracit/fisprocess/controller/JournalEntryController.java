package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.dto.request.CorrectionRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.ReversalRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.ReversalResponseDto;
import com.bracit.fisprocess.service.JournalReversalService;
import com.bracit.fisprocess.service.JournalEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for Journal Entry operations.
 */
@RestController
@RequestMapping("/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final JournalReversalService journalReversalService;

    @PostMapping
    public ResponseEntity<JournalEntryResponseDto> createJournalEntry(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Role", required = false) @Nullable String actorRole,
            @RequestHeader(value = "traceparent", required = false) @Nullable String traceparent,
            @Valid @RequestBody CreateJournalEntryRequestDto request) {
        JournalEntryResponseDto response = journalEntryService.createJournalEntry(tenantId, request, actorRole, traceparent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponseDto> getJournalEntry(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        JournalEntryResponseDto response = journalEntryService.getJournalEntry(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<JournalEntryResponseDto>> listJournalEntries(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable LocalDate postedDateFrom,
            @RequestParam(required = false) @Nullable LocalDate postedDateTo,
            @RequestParam(required = false) @Nullable String accountCode,
            @RequestParam(required = false) @Nullable JournalStatus status,
            @RequestParam(required = false) @Nullable String referenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<JournalEntryResponseDto> result = journalEntryService.listJournalEntries(
                tenantId, postedDateFrom, postedDateTo, accountCode, status, referenceId,
                PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ReversalResponseDto> reverseJournalEntry(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ReversalRequestDto request) {
        ReversalResponseDto response = journalReversalService.reverse(tenantId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/correct")
    public ResponseEntity<ReversalResponseDto> correctJournalEntry(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CorrectionRequestDto request) {
        ReversalResponseDto response = journalReversalService.correct(tenantId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
