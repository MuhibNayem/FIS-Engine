package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated statistics for the outbox and dead-letter queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxStatsResponseDto {

    private long unpublishedCount;
    private long dlqSize;
    private long oldestUnpublishedAgeSeconds;
    private int retryStreak;
}
