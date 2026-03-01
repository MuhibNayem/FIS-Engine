package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single currency line in an FX Exposure report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FxExposureLineDto {

    private String currency;
    private long assetExposure;
    private long liabilityExposure;
    private long netExposure;
    private BigDecimal currentRate;
    private long unrealizedGainLoss;
}
