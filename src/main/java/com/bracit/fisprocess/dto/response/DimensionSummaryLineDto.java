package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single line in a Dimension Summary report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionSummaryLineDto {

    private String dimensionValue;
    private long debitTotal;
    private long creditTotal;
    private long netAmount;
}
