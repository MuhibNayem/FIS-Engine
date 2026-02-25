package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import com.bracit.fisprocess.dto.response.EventIngestionResponseDto;
import com.bracit.fisprocess.service.FinancialEventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Event ingestion endpoint.
 */
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventIngestionController {

    private final FinancialEventIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<EventIngestionResponseDto> ingest(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader("X-Source-System") String sourceSystem,
            @Valid @RequestBody FinancialEventRequestDto request) {
        EventIngestionResponseDto response = ingestionService.ingest(tenantId, sourceSystem, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
