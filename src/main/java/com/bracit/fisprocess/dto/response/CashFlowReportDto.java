package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cash Flow Statement — inflows/outflows by operating, investing, financing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowReportDto {

    private ReportMetadataDto metadata;
    private CashFlowSectionDto operatingActivities;
    private CashFlowSectionDto investingActivities;
    private CashFlowSectionDto financingActivities;
    private long netCashChange;
    private long openingCash;
    private long closingCash;
}
