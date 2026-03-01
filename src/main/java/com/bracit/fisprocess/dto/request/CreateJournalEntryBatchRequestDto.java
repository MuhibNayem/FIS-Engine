package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.JournalBatchMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for batch manual journal entry posting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJournalEntryBatchRequestDto {

    @NotNull(message = "batchMode is required")
    private JournalBatchMode batchMode;

    @NotEmpty(message = "At least one journal entry is required")
    @Valid
    private List<CreateJournalEntryRequestDto> entries;
}
