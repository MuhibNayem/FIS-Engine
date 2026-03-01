package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Balance Sheet — Assets = Liabilities + Equity at a point in time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetReportDto {

    private ReportMetadataDto metadata;
    private BalanceSheetSectionDto assets;
    private BalanceSheetSectionDto liabilities;
    private BalanceSheetSectionDto equity;
    private long totalAssets;
    private long totalLiabilitiesAndEquity;
    private boolean balanced;
}
