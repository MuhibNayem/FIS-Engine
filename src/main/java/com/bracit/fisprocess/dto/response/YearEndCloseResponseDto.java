package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for a completed fiscal year-end close operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearEndCloseResponseDto {

    private Integer fiscalYear;
    private UUID closingJournalEntryId;
    private long totalRevenue;
    private long totalExpenses;
    private long netIncome;
    private int accountsClosed;
    private String retainedEarningsAccountCode;
    private String message;
}
