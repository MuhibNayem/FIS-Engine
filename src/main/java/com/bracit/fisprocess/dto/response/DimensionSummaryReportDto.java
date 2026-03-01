package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dimension Summary report — aggregate income/expense by dimension tags.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DimensionSummaryReportDto {

    private ReportMetadataDto metadata;
    private String dimensionKey;
    private List<DimensionSummaryLineDto> lines;
    private long grandTotal;
}
