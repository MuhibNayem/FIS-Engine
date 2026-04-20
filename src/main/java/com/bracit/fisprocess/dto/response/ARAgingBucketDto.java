package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single bucket in the AR Aging report (e.g., "0-30", "31-60").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ARAgingBucketDto {

    private String bucket;
    private int count;
    private Long totalAmount;
}
