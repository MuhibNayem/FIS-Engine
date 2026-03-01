package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResponseDto {

    private UUID periodId;
    private UUID runId;
    private String status;
    private List<UUID> generatedJournalEntryIds;
}
