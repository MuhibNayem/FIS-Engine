package com.bracit.fisprocess.dto.response;

import com.bracit.fisprocess.domain.enums.JournalBatchMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for batch manual journal entry processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryBatchResponseDto {

    private JournalBatchMode batchMode;
    private int count;
    private List<JournalEntryResponseDto> entries;
}
