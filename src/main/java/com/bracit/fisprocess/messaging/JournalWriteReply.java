package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
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
public class JournalWriteReply {

    private UUID trackingId;

    private boolean success;

    @Nullable
    private JournalEntryResponseDto journalEntry;

    @Nullable
    private String errorMessage;

    @Nullable
    private String errorCode;
}