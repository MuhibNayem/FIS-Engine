package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Income Statement (P&amp;L) — Revenue − Expenses over a date range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementReportDto {

    private ReportMetadataDto metadata;
    private IncomeStatementSectionDto revenue;
    private IncomeStatementSectionDto expenses;
    private long totalRevenue;
    private long totalExpenses;
    private long netIncome;
}
