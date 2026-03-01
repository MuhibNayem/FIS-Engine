package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Trial Balance report — verifies Σ Debits = Σ Credits across all accounts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceReportDto {

    private ReportMetadataDto metadata;
    private List<TrialBalanceLineDto> lines;
    private long totalDebits;
    private long totalCredits;
    private boolean balanced;
}
