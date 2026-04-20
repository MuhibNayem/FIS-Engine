package com.bracit.fisprocess.service;

import java.time.LocalDate;
import java.util.UUID;

public interface BudgetService {
    com.bracit.fisprocess.dto.response.BudgetResponseDto create(UUID tenantId, com.bracit.fisprocess.dto.request.CreateBudgetRequestDto req, String performedBy);
    com.bracit.fisprocess.dto.response.BudgetResponseDto approve(UUID tenantId, UUID id);
    com.bracit.fisprocess.dto.response.BudgetVarianceResponseDto getVariance(UUID tenantId, UUID budgetId);
    com.bracit.fisprocess.dto.response.BudgetResponseDto getById(UUID tenantId, UUID id);
    org.springframework.data.domain.Page<com.bracit.fisprocess.dto.response.BudgetResponseDto> list(UUID tenantId, org.springframework.data.domain.Pageable pageable);
    void validateBudgetThreshold(UUID tenantId, String accountCode, long amount, LocalDate date);
}
