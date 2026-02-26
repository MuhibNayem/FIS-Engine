package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.ExchangeRateUploadDto;
import com.bracit.fisprocess.dto.response.ExchangeRateResponseDto;
import com.bracit.fisprocess.service.ExchangeRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @PostMapping
    public ResponseEntity<List<ExchangeRateResponseDto>> upload(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody ExchangeRateUploadDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exchangeRateService.upload(tenantId, request));
    }

    @GetMapping
    public ResponseEntity<List<ExchangeRateResponseDto>> query(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency,
            @RequestParam(required = false) @Nullable LocalDate effectiveDate) {
        return ResponseEntity.ok(exchangeRateService.query(tenantId, sourceCurrency, targetCurrency, effectiveDate));
    }
}
