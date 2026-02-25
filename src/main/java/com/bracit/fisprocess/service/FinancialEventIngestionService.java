package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;

import java.util.UUID;

/**
 * Ingests financial events via REST and publishes them to RabbitMQ.
 */
public interface FinancialEventIngestionService {

    EventIngestionResponseDto ingest(UUID tenantId, String sourceSystem, FinancialEventRequestDto request);
}
