package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * FX Exposure report — open foreign-currency positions with unrealized
 * gain/loss.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxExposureReportDto {

    private ReportMetadataDto metadata;
    private String baseCurrency;
    private List<FxExposureLineDto> exposures;
    private long totalUnrealizedGainLoss;
}
