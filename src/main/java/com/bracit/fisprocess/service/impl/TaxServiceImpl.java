package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.TaxGroup;
import com.bracit.fisprocess.domain.entity.TaxGroupRate;
import com.bracit.fisprocess.domain.entity.TaxRate;
import com.bracit.fisprocess.domain.enums.TaxType;
import com.bracit.fisprocess.dto.request.CreateTaxGroupRequestDto;
import com.bracit.fisprocess.dto.request.CreateTaxRateRequestDto;
import com.bracit.fisprocess.dto.request.TaxGroupRateRequestDto;
import com.bracit.fisprocess.dto.response.TaxCalculationResponseDto;
import com.bracit.fisprocess.dto.response.TaxCalculationResponseDto.TaxBreakdownDto;
import com.bracit.fisprocess.dto.response.TaxGroupResponseDto;
import com.bracit.fisprocess.dto.response.TaxGroupResponseDto.TaxGroupRateResponseDto;
import com.bracit.fisprocess.exception.InvalidTaxCalculationException;
import com.bracit.fisprocess.exception.TaxGroupNotFoundException;
import com.bracit.fisprocess.exception.TaxRateNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.TaxGroupRateRepository;
import com.bracit.fisprocess.repository.TaxGroupRepository;
import com.bracit.fisprocess.repository.TaxRateRepository;
import com.bracit.fisprocess.service.TaxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of {@link TaxService} for Tax calculation and rate management.
 * <p>
 * Supports tax-exclusive, tax-inclusive, and compound tax calculations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxServiceImpl implements TaxService {

    private final TaxRateRepository taxRateRepository;
    private final TaxGroupRepository taxGroupRepository;
    private final TaxGroupRateRepository taxGroupRateRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public TaxRate createTaxRate(UUID tenantId, CreateTaxRateRequestDto request) {
        validateTenantExists(tenantId);
        validateTaxRateCodeUniqueness(tenantId, request.getCode());

        TaxRate taxRate = TaxRate.builder()
                .tenantId(tenantId)
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .rate(request.getRate())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .type(request.getType())
                .isActive(true)
                .build();

        TaxRate saved = taxRateRepository.save(taxRate);
        log.info("Created tax rate '{}' for tenant '{}'", saved.getCode(), tenantId);
        return saved;
    }

    @Override
    @Transactional
    public TaxGroup createTaxGroup(UUID tenantId, CreateTaxGroupRequestDto request) {
        validateTenantExists(tenantId);

        TaxGroup taxGroup = TaxGroup.builder()
                .tenantId(tenantId)
                .name(request.getName().trim())
                .description(request.getDescription())
                .build();

        TaxGroup saved = taxGroupRepository.save(taxGroup);

        // Create group rates
        for (TaxGroupRateRequestDto rateDto : request.getRates()) {
            // Validate tax rate exists and belongs to tenant
            TaxRate rate = taxRateRepository.findById(rateDto.getTaxRateId())
                    .filter(tr -> tr.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new TaxRateNotFoundException(rateDto.getTaxRateId()));

            TaxGroupRate groupRate = TaxGroupRate.builder()
                    .group(saved)
                    .taxRateId(rateDto.getTaxRateId())
                    .isCompound(rateDto.getIsCompound())
                    .build();

            taxGroupRateRepository.save(groupRate);
        }

        log.info("Created tax group '{}' for tenant '{}'", saved.getName(), tenantId);
        return saved;
    }

    @Override
    public TaxCalculationResponseDto calculate(
            UUID tenantId,
            Long amount,
            UUID taxGroupId,
            boolean isInclusive) {
        validateTenantExists(tenantId);

        // Load tax group and validate ownership
        TaxGroup taxGroup = taxGroupRepository.findById(taxGroupId)
                .filter(tg -> tg.getTenantId().equals(tenantId))
                .orElseThrow(() -> new TaxGroupNotFoundException(taxGroupId));

        // Load group rates
        List<TaxGroupRate> groupRates = taxGroupRateRepository.findByGroupId(taxGroupId);
        if (groupRates.isEmpty()) {
            throw new InvalidTaxCalculationException(taxGroupId, "Tax group has no rates configured");
        }

        // Load active tax rates
        List<UUID> rateIds = groupRates.stream()
                .map(TaxGroupRate::getTaxRateId)
                .collect(Collectors.toList());
        List<TaxRate> taxRates = taxRateRepository.findActiveRatesByIdsAndDate(rateIds, LocalDate.now());

        if (taxRates.isEmpty()) {
            throw new InvalidTaxCalculationException(taxGroupId, "No active tax rates found for today's date");
        }

        List<TaxBreakdownDto> breakdown = new ArrayList<>();
        long totalTax = 0L;
        long taxableAmount = isInclusive ? amount : amount;

        // Calculate tax for each rate
        for (TaxGroupRate groupRate : groupRates) {
            TaxRate rate = taxRates.stream()
                    .filter(r -> r.getTaxRateId().equals(groupRate.getTaxRateId()))
                    .findFirst()
                    .orElse(null);

            if (rate == null) {
                continue;
            }

            long taxAmount;
            if (isInclusive) {
                // Tax-inclusive: tax = grossAmount × rate / (1 + rate)
                BigDecimal rateDecimal = rate.getRate();
                BigDecimal grossAmount = BigDecimal.valueOf(amount);
                BigDecimal onePlusRate = BigDecimal.ONE.add(rateDecimal);
                BigDecimal taxDecimal = grossAmount.multiply(rateDecimal)
                        .divide(onePlusRate, 0, RoundingMode.HALF_UP);
                taxAmount = taxDecimal.longValue();
            } else {
                // Tax-exclusive: tax = netAmount × rate
                BigDecimal netAmount = BigDecimal.valueOf(amount);
                BigDecimal taxDecimal = netAmount.multiply(rate.getRate())
                        .setScale(0, RoundingMode.HALF_UP);
                taxAmount = taxDecimal.longValue();
            }

            // Compound tax: subsequent taxes apply to base + previous taxes
            if (groupRate.getIsCompound()) {
                // Already handled — compound tax applies to the current base
                // which includes previous non-compound taxes
            }

            totalTax += taxAmount;

            breakdown.add(new TaxBreakdownDto(
                    rate.getCode(),
                    taxAmount,
                    groupRate.getIsCompound()));

            // For compound rates, the next rate's base includes this tax
            if (groupRate.getIsCompound()) {
                taxableAmount += taxAmount;
            }
        }

        return TaxCalculationResponseDto.builder()
                .taxableAmount(taxableAmount)
                .totalTax(totalTax)
                .breakdown(breakdown)
                .build();
    }

    @Override
    public BigDecimal getEffectiveRate(UUID tenantId, UUID taxGroupId) {
        validateTenantExists(tenantId);

        TaxGroup taxGroup = taxGroupRepository.findById(taxGroupId)
                .filter(tg -> tg.getTenantId().equals(tenantId))
                .orElseThrow(() -> new TaxGroupNotFoundException(taxGroupId));

        List<TaxGroupRate> groupRates = taxGroupRateRepository.findByGroupId(taxGroupId);
        if (groupRates.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<UUID> rateIds = groupRates.stream()
                .map(TaxGroupRate::getTaxRateId)
                .collect(Collectors.toList());
        List<TaxRate> taxRates = taxRateRepository.findActiveRatesByIdsAndDate(rateIds, LocalDate.now());

        // Sum up rates (compound rates are applied sequentially)
        BigDecimal effectiveRate = BigDecimal.ZERO;
        for (TaxGroupRate groupRate : groupRates) {
            TaxRate rate = taxRates.stream()
                    .filter(r -> r.getTaxRateId().equals(groupRate.getTaxRateId()))
                    .findFirst()
                    .orElse(null);

            if (rate != null) {
                if (groupRate.getIsCompound()) {
                    // Compound: effective = rate1 + rate2 + rate1 * rate2
                    effectiveRate = effectiveRate.add(rate.getRate())
                            .add(effectiveRate.multiply(rate.getRate()));
                } else {
                    effectiveRate = effectiveRate.add(rate.getRate());
                }
            }
        }

        return effectiveRate;
    }

    @Override
    public TaxRate getTaxRate(UUID tenantId, UUID taxRateId) {
        return taxRateRepository.findById(taxRateId)
                .filter(tr -> tr.getTenantId().equals(tenantId))
                .orElseThrow(() -> new TaxRateNotFoundException(taxRateId));
    }

    @Override
    public TaxGroup getTaxGroup(UUID tenantId, UUID taxGroupId) {
        return taxGroupRepository.findById(taxGroupId)
                .filter(tg -> tg.getTenantId().equals(tenantId))
                .orElseThrow(() -> new TaxGroupNotFoundException(taxGroupId));
    }

    @Override
    public Page<TaxRate> listTaxRates(
            UUID tenantId,
            @Nullable TaxType type,
            @Nullable Boolean isActive,
            Pageable pageable) {
        validateTenantExists(tenantId);
        return taxRateRepository.findByTenantIdWithFilters(tenantId, type, isActive, pageable);
    }

    @Override
    public Page<TaxGroupResponseDto> listTaxGroups(UUID tenantId, Pageable pageable) {
        validateTenantExists(tenantId);
        return taxGroupRepository.findByTenantId(tenantId, pageable)
                .map(this::toGroupResponseDto);
    }

    // --- Private Helper Methods ---

    private void validateTenantExists(UUID tenantId) {
        businessEntityRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));
    }

    private void validateTaxRateCodeUniqueness(UUID tenantId, String code) {
        if (taxRateRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException(
                    "Tax rate code '" + code + "' already exists for this tenant");
        }
    }

    private TaxGroupResponseDto toGroupResponseDto(TaxGroup taxGroup) {
        TaxGroupResponseDto dto = modelMapper.map(taxGroup, TaxGroupResponseDto.class);
        dto.setTaxGroupId(taxGroup.getTaxGroupId());

        // Load rates
        List<TaxGroupRate> rates = taxGroupRateRepository.findByGroupId(taxGroup.getTaxGroupId());
        dto.setRates(rates.stream().map(gr -> {
            TaxRate rate = taxRateRepository.findById(gr.getTaxRateId()).orElse(null);
            return TaxGroupRateResponseDto.builder()
                    .taxGroupRateId(gr.getTaxGroupRateId())
                    .taxRateId(gr.getTaxRateId())
                    .taxRateCode(rate != null ? rate.getCode() : null)
                    .isCompound(gr.getIsCompound())
                    .build();
        }).collect(Collectors.toList()));

        return dto;
    }
}
