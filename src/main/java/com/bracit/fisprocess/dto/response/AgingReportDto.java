package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aging Analysis report — outstanding balances bucketed by age.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingReportDto {

    private ReportMetadataDto metadata;
    private String accountType;
    private List<AgingBucketDto> buckets;
    private long grandTotal;
}
