package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.RunRevaluationRequestDto;
import com.bracit.fisprocess.dto.request.RunTranslationRequestDto;
import com.bracit.fisprocess.dto.response.RevaluationResponseDto;
import com.bracit.fisprocess.dto.response.TranslationResponseDto;
import com.bracit.fisprocess.service.FunctionalCurrencyTranslationService;
import com.bracit.fisprocess.service.PeriodEndRevaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/revaluations")
@RequiredArgsConstructor
public class RevaluationController {

    private final PeriodEndRevaluationService periodEndRevaluationService;
    private final FunctionalCurrencyTranslationService functionalCurrencyTranslationService;

    @PostMapping("/periods/{periodId}")
    public ResponseEntity<RevaluationResponseDto> run(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID periodId,
            @Valid @RequestBody RunRevaluationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodEndRevaluationService.run(tenantId, periodId, request));
    }

    @PostMapping("/periods/{periodId}/translation")
    public ResponseEntity<TranslationResponseDto> runTranslation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID periodId,
            @Valid @RequestBody RunTranslationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(functionalCurrencyTranslationService.run(tenantId, periodId, request));
    }
}
