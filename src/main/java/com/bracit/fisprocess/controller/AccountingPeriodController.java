package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateAccountingPeriodRequestDto;
import com.bracit.fisprocess.dto.request.PeriodStatusChangeRequestDto;
import com.bracit.fisprocess.dto.response.AccountingPeriodResponseDto;
import com.bracit.fisprocess.service.AccountingPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounting-periods")
@RequiredArgsConstructor
public class AccountingPeriodController {

    private final AccountingPeriodService accountingPeriodService;

    @PostMapping
    public ResponseEntity<AccountingPeriodResponseDto> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateAccountingPeriodRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountingPeriodService.createPeriod(tenantId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountingPeriodResponseDto>> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable PeriodStatus status) {
        return ResponseEntity.ok(accountingPeriodService.listPeriods(tenantId, status));
    }

    @PatchMapping("/{periodId}/status")
    public ResponseEntity<AccountingPeriodResponseDto> changeStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String changedBy,
            @PathVariable UUID periodId,
            @Valid @RequestBody PeriodStatusChangeRequestDto request) {
        return ResponseEntity.ok(accountingPeriodService.changeStatus(tenantId, periodId, request.getStatus(), changedBy));
    }
}
