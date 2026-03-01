package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.YearEndCloseRequestDto;
import com.bracit.fisprocess.dto.response.LedgerIntegrityCheckResponseDto;
import com.bracit.fisprocess.dto.response.YearEndCloseResponseDto;
import com.bracit.fisprocess.service.LedgerIntegrityService;
import com.bracit.fisprocess.service.YearEndCloseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminIntegrityController {

    private final LedgerIntegrityService ledgerIntegrityService;
    private final YearEndCloseService yearEndCloseService;

    @GetMapping("/integrity-check")
    public ResponseEntity<LedgerIntegrityCheckResponseDto> integrityCheck(
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return ResponseEntity.ok(ledgerIntegrityService.checkTenant(tenantId));
    }

    /**
     * Performs a fiscal year-end close.
     * <p>
     * Generates a closing Journal Entry that zeroes out all Revenue/Expense account
     * balances and transfers the net income to the specified Retained Earnings
     * account.
     * <p>
     * Requires all accounting periods in the fiscal year to be HARD_CLOSED.
     * Requires FIS_ADMIN role.
     */
    @PostMapping("/year-end-close")
    public ResponseEntity<YearEndCloseResponseDto> yearEndClose(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody YearEndCloseRequestDto request) {
        return ResponseEntity.ok(yearEndCloseService.performYearEndClose(tenantId, request));
    }
}
