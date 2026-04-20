package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Tax Calculation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCalculationResponseDto {

    private Long taxableAmount;
    private Long totalTax;
    private List<TaxBreakdownDto> breakdown;

    /**
     * Inner DTO for a single tax breakdown line.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxBreakdownDto {
        private String taxRateCode;
        private Long taxAmount;
        private Boolean isCompound;
    }
}
