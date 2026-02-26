package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.response.LedgerIntegrityCheckResponseDto;
import com.bracit.fisprocess.service.LedgerIntegrityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminIntegrityController {

    private final LedgerIntegrityService ledgerIntegrityService;

    @GetMapping("/integrity-check")
    public ResponseEntity<LedgerIntegrityCheckResponseDto> integrityCheck(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(ledgerIntegrityService.checkTenant(tenantId));
    }
}
