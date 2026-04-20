package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tax Liability report — aggregated tax payable/receivable over a period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxLiabilityReportDto {

    private String jurisdictionId;
    private String fromDate;
    private String toDate;
    private Long totalOutputTax;
    private Long totalInputTax;
    private Long netPayable;
    private List<TaxLiabilityLineDto> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxLiabilityLineDto {
        private String taxRateCode;
        private Long taxableAmount;
        private Long taxAmount;
        private String direction;
    }
}
