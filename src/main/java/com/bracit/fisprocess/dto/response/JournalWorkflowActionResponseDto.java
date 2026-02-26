package com.bracit.fisprocess.dto.response;

import com.bracit.fisprocess.domain.enums.JournalWorkflowStatus;
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
public class JournalWorkflowActionResponseDto {

    private UUID workflowId;
    private JournalWorkflowStatus status;
    @Nullable
    private UUID postedJournalEntryId;
    private String message;
}
