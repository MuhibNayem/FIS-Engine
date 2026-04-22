package com.bracit.fisprocess.dto.response;

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
public class AsyncJobResponseDto {

    private UUID trackingId;

    private String status;

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