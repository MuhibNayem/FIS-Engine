package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for /v1/events ingestion endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventIngestionResponseDto {
    private String status;
    private String ik;
    private String message;
}
