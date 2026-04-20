package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single aging bucket for AP Aging report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APAgingBucketDto {

    private String bucket;
    private int count;
    private Long amount;
}
