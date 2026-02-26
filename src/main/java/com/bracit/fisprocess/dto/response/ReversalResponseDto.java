package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalResponseDto {

    private UUID reversalJournalEntryId;
    private UUID originalJournalEntryId;
    private String status;
    private String message;
}
