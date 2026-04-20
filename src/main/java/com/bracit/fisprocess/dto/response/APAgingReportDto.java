package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AP Aging report — outstanding balances bucketed by age.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APAgingReportDto {

    private String asOfDate;
    private String vendorId;
    private String currency;
    private List<APAgingBucketDto> buckets;
    private Long totalOutstanding;
}
