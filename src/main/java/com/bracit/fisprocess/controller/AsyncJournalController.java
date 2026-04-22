package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.response.AsyncJobResponseDto;
import com.bracit.fisprocess.service.AsyncJournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/async/journal-entries")
@RequiredArgsConstructor
@ApiVersion(1)
public class AsyncJournalController {

    private final AsyncJournalService asyncJournalService;

    @PostMapping
    public ResponseEntity<AsyncJobResponseDto> submitAsyncJournalEntry(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Role", required = false) @Nullable String actorRole,
            @RequestHeader(value = "traceparent", required = false) @Nullable String traceparent,
            @Valid @RequestBody CreateJournalEntryRequestDto request) {
        AsyncJobResponseDto response = asyncJournalService.submitAsyncJournalEntry(
                tenantId, request, actorRole, traceparent);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<AsyncJobResponseDto> submitAsyncJournalEntryBatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Role", required = false) @Nullable String actorRole,
            @RequestHeader(value = "traceparent", required = false) @Nullable String traceparent,
            @Valid @RequestBody CreateJournalEntryBatchRequestDto request) {
        AsyncJobResponseDto response = asyncJournalService.submitAsyncJournalEntryBatch(
                tenantId, request, actorRole, traceparent);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{trackingId}/status")
    public ResponseEntity<AsyncJobResponseDto> getJobStatus(@PathVariable UUID trackingId) {
        AsyncJobResponseDto response = asyncJournalService.getJobStatus(trackingId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }
}