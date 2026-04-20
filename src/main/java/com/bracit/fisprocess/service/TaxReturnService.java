package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.TaxReturn;
import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import com.bracit.fisprocess.dto.request.GenerateTaxReturnRequestDto;
import com.bracit.fisprocess.dto.response.TaxLiabilityReportDto;
import com.bracit.fisprocess.dto.response.TaxReturnResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Orchestrator service for Tax Return operations.
 */
public interface TaxReturnService {

    /**
     * Generates a new Tax Return for a jurisdiction and period.
     */
    TaxReturn generate(UUID tenantId, GenerateTaxReturnRequestDto request, String performedBy);

    /**
     * Files a tax return — transitions status to FILED.
     */
    TaxReturn file(UUID tenantId, UUID taxReturnId, String performedBy);

    /**
     * Retrieves a tax return by ID, validating tenant ownership.
     */
    TaxReturn getById(UUID tenantId, UUID taxReturnId);

    /**
     * Lists tax returns for a tenant with optional filters.
     */
    Page<TaxReturnResponseDto> list(
            UUID tenantId,
            @Nullable UUID jurisdictionId,
            @Nullable TaxReturnStatus status,
            Pageable pageable);

    /**
     * Generates a tax liability report for a jurisdiction and date range.
     */
    TaxLiabilityReportDto getLiabilityReport(
            UUID tenantId,
            UUID jurisdictionId,
            LocalDate fromDate,
            LocalDate toDate);
}
