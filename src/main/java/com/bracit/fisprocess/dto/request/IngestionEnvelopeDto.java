package com.bracit.fisprocess.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Message payload pushed to RabbitMQ ingestion queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionEnvelopeDto {
    private UUID tenantId;
    private String sourceSystem;
    private String payloadHash;
    private FinancialEventRequestDto event;
}
