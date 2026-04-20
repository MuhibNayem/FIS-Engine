package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AR Aging report — outstanding balances bucketed by age.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ARAgingReportDto {

    private String asOfDate;
    private String customerId;
    private String currency;
    private List<ARAgingBucketDto> buckets;
    private Long totalOutstanding;
}
