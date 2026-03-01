package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single bucket in an Aging report (e.g., 0-30 days, 31-60 days).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingBucketDto {

    private String bucketLabel;
    private long amountCents;
    private long entryCount;
}
