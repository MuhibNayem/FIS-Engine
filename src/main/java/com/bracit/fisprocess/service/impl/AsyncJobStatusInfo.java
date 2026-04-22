package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.enums.AsyncJobStatus;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobStatusInfo {

    private UUID trackingId;

    private AsyncJobStatus status;

    private UUID tenantId;

    private OffsetDateTime submittedAt;

    @Nullable
    private OffsetDateTime processedAt;

    @Nullable
    private JournalEntryResponseDto journalEntry;

    @Nullable
    private String errorMessage;

    @Nullable
    private String errorCode;
}