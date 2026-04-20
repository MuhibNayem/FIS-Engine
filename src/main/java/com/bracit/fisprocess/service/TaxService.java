package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.TaxGroup;
import com.bracit.fisprocess.domain.entity.TaxRate;
import com.bracit.fisprocess.domain.enums.TaxType;
import com.bracit.fisprocess.dto.request.CreateTaxGroupRequestDto;
import com.bracit.fisprocess.dto.request.CreateTaxRateRequestDto;
import com.bracit.fisprocess.dto.response.TaxCalculationResponseDto;
import com.bracit.fisprocess.dto.response.TaxGroupResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for Tax calculation and rate management.
 */
public interface TaxService {

    /**
     * Creates a new Tax Rate.
     */
    TaxRate createTaxRate(UUID tenantId, CreateTaxRateRequestDto request);

    /**
     * Creates a new Tax Group with its rates.
     */
    TaxGroup createTaxGroup(UUID tenantId, CreateTaxGroupRequestDto request);

    /**
     * Calculates tax for a given amount and tax group.
     *
     * @param tenantId     the tenant UUID
     * @param amount       the amount (in cents)
     * @param taxGroupId   the tax group ID
     * @param isInclusive  whether the amount is tax-inclusive
     * @return the tax calculation result with breakdown
     */
    TaxCalculationResponseDto calculate(
            UUID tenantId,
            Long amount,
            UUID taxGroupId,
            boolean isInclusive);

    /**
     * Gets the effective combined rate for a tax group.
     */
    BigDecimal getEffectiveRate(UUID tenantId, UUID taxGroupId);

    /**
     * Retrieves a tax rate by ID, validating tenant ownership.
     */
    TaxRate getTaxRate(UUID tenantId, UUID taxRateId);

    /**
     * Retrieves a tax group by ID, validating tenant ownership.
     */
    TaxGroup getTaxGroup(UUID tenantId, UUID taxGroupId);

    /**
     * Lists tax rates for a tenant with optional filters.
     */
    Page<TaxRate> listTaxRates(
            UUID tenantId,
            @Nullable TaxType type,
            @Nullable Boolean isActive,
            Pageable pageable);

    /**
     * Lists tax groups for a tenant.
     */
    Page<TaxGroupResponseDto> listTaxGroups(UUID tenantId, Pageable pageable);
}
