package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalWriteMessage {

    private UUID trackingId;

    private UUID tenantId;

    @Nullable
    private String actorRoleHeader;

    @Nullable
    private String traceparent;

    private CreateJournalEntryRequestDto request;
}