package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.SettlementRequestDto;
import com.bracit.fisprocess.dto.response.SettlementResponseDto;
import com.bracit.fisprocess.service.SettlementService;
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

@RestController
@RequestMapping("/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @PostMapping
    public ResponseEntity<SettlementResponseDto> settle(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody SettlementRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settlementService.settle(tenantId, request));
    }
}
