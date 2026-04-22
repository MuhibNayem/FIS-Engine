package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.AsyncJobStatus;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.messaging.JournalWriteReply;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AsyncJobStatusService {

    void createPendingJob(UUID trackingId, UUID tenantId);

    void markProcessing(UUID trackingId);

    void markCompleted(UUID trackingId, JournalEntryResponseDto journalEntry);

    void markFailed(UUID trackingId, String errorMessage, @Nullable String errorCode);

    @Nullable
    JournalWriteReply getJobStatus(UUID trackingId);

    @Nullable
    AsyncJobStatus getStatus(UUID trackingId);

    OffsetDateTime getSubmittedAt(UUID trackingId);

    void cleanupStaleJobs();
}