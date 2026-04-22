package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.CreateJournalEntryBatchRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.messaging.JournalWriteReply;
import com.bracit.fisprocess.dto.response.AsyncJobResponseDto;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface AsyncJournalService {

    AsyncJobResponseDto submitAsyncJournalEntry(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent);

    AsyncJobResponseDto submitAsyncJournalEntryBatch(
            UUID tenantId,
            CreateJournalEntryBatchRequestDto request,
            @Nullable String actorRoleHeader,
            @Nullable String traceparent);

    AsyncJobResponseDto getJobStatus(UUID trackingId);
}