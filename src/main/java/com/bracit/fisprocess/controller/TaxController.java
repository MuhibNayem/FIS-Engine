package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.TaxGroup;
import com.bracit.fisprocess.domain.entity.TaxRate;
import com.bracit.fisprocess.domain.entity.TaxReturn;
import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import com.bracit.fisprocess.domain.enums.TaxType;
import com.bracit.fisprocess.dto.request.CalculateTaxRequestDto;
import com.bracit.fisprocess.dto.request.CreateTaxGroupRequestDto;
import com.bracit.fisprocess.dto.request.CreateTaxRateRequestDto;
import com.bracit.fisprocess.dto.request.GenerateTaxReturnRequestDto;
import com.bracit.fisprocess.dto.response.TaxCalculationResponseDto;
import com.bracit.fisprocess.dto.response.TaxGroupResponseDto;
import com.bracit.fisprocess.dto.response.TaxLiabilityReportDto;
import com.bracit.fisprocess.dto.response.TaxRateResponseDto;
import com.bracit.fisprocess.dto.response.TaxReturnResponseDto;
import com.bracit.fisprocess.service.TaxReturnService;
import com.bracit.fisprocess.service.TaxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for Tax Engine operations.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/tax")
@RequiredArgsConstructor
@ApiVersion(1)
public class TaxController {

    private final TaxService taxService;
    private final TaxReturnService taxReturnService;
    private final ModelMapper modelMapper;

    // --- Tax Calculation ---

    /**
     * Calculates tax for a given amount and tax group.
     *
     * @param tenantId the tenant UUID
     * @param request  the tax calculation request
     * @return 200 OK with the tax calculation result
     */
    @PostMapping("/calculate")
    public ResponseEntity<TaxCalculationResponseDto> calculate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CalculateTaxRequestDto request) {
        TaxCalculationResponseDto result = taxService.calculate(
                tenantId, request.getAmount(), request.getTaxGroupId(), request.getIsInclusive());
        return ResponseEntity.ok(result);
    }

    // --- Tax Rates ---

    /**
     * Creates a new Tax Rate.
     *
     * @param tenantId the tenant UUID
     * @param request  the tax rate creation details
     * @return 201 Created with the new tax rate
     */
    @PostMapping("/rates")
    public ResponseEntity<TaxRateResponseDto> createTaxRate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateTaxRateRequestDto request) {
        TaxRate taxRate = taxService.createTaxRate(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTaxRateResponseDto(taxRate));
    }

    /**
     * Lists tax rates for a tenant with optional filters.
     *
     * @param tenantId the tenant UUID
     * @param type     optional tax type filter
     * @param isActive optional active status filter
     * @param pageable pagination parameters
     * @return 200 OK with paginated tax rate list
     */
    @GetMapping("/rates")
    public ResponseEntity<Page<TaxRateResponseDto>> listTaxRates(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable TaxType type,
            @RequestParam(required = false) @Nullable Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TaxRateResponseDto> response = taxService.listTaxRates(tenantId, type, isActive, pageable)
                .map(this::toTaxRateResponseDto);
        return ResponseEntity.ok(response);
    }

    // --- Tax Groups ---

    /**
     * Creates a new Tax Group.
     *
     * @param tenantId the tenant UUID
     * @param request  the tax group creation details
     * @return 201 Created with the new tax group
     */
    @PostMapping("/groups")
    public ResponseEntity<TaxGroupResponseDto> createTaxGroup(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateTaxGroupRequestDto request) {
        TaxGroup taxGroup = taxService.createTaxGroup(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                taxService.listTaxGroups(tenantId, Pageable.ofSize(1)).stream()
                        .filter(g -> g.getTaxGroupId().equals(taxGroup.getTaxGroupId()))
                        .findFirst()
                        .orElse(modelMapper.map(taxGroup, TaxGroupResponseDto.class)));
    }

    /**
     * Lists tax groups for a tenant.
     *
     * @param tenantId the tenant UUID
     * @param pageable pagination parameters
     * @return 200 OK with paginated tax group list
     */
    @GetMapping("/groups")
    public ResponseEntity<Page<TaxGroupResponseDto>> listTaxGroups(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TaxGroupResponseDto> response = taxService.listTaxGroups(tenantId, pageable);
        return ResponseEntity.ok(response);
    }

    // --- Tax Returns ---

    /**
     * Lists tax returns for a tenant with optional filters.
     *
     * @param tenantId       the tenant UUID
     * @param jurisdictionId optional jurisdiction filter
     * @param status         optional status filter
     * @param pageable       pagination parameters
     * @return 200 OK with paginated tax return list
     */
    @GetMapping("/returns")
    public ResponseEntity<Page<TaxReturnResponseDto>> listTaxReturns(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID jurisdictionId,
            @RequestParam(required = false) @Nullable TaxReturnStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<TaxReturnResponseDto> response = taxReturnService.list(tenantId, jurisdictionId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a new Tax Return for a jurisdiction and period.
     *
     * @param tenantId the tenant UUID
     * @param request  the tax return generation details
     * @return 201 Created with the new tax return
     */
    @PostMapping("/returns/generate")
    public ResponseEntity<TaxReturnResponseDto> generateTaxReturn(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody GenerateTaxReturnRequestDto request) {
        TaxReturn taxReturn = taxReturnService.generate(tenantId, request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                taxReturnService.list(tenantId, null, null, Pageable.ofSize(1)).stream()
                        .filter(tr -> tr.getTaxReturnId().equals(taxReturn.getTaxReturnId()))
                        .findFirst()
                        .orElse(modelMapper.map(taxReturn, TaxReturnResponseDto.class)));
    }

    /**
     * Files a tax return — transitions status to FILED.
     *
     * @param tenantId     the tenant UUID
     * @param taxReturnId  the tax return UUID
     * @return 200 OK with the filed tax return
     */
    @PostMapping("/returns/{id}/file")
    public ResponseEntity<TaxReturnResponseDto> fileTaxReturn(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable("id") UUID taxReturnId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        TaxReturn taxReturn = taxReturnService.file(tenantId, taxReturnId, performedBy);
        return ResponseEntity.ok(modelMapper.map(taxReturn, TaxReturnResponseDto.class));
    }

    // --- Tax Reports ---

    /**
     * Generates a tax liability report for a jurisdiction and date range.
     *
     * @param tenantId       the tenant UUID
     * @param jurisdictionId the jurisdiction UUID
     * @param fromDate       the start date
     * @param toDate         the end date
     * @return 200 OK with the tax liability report
     */
    @GetMapping("/reports/liability")
    public ResponseEntity<TaxLiabilityReportDto> getLiabilityReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID jurisdictionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        TaxLiabilityReportDto report = taxReturnService.getLiabilityReport(
                tenantId, jurisdictionId, fromDate, toDate);
        return ResponseEntity.ok(report);
    }

    // --- Helper Methods ---

    private TaxRateResponseDto toTaxRateResponseDto(TaxRate taxRate) {
        TaxRateResponseDto dto = modelMapper.map(taxRate, TaxRateResponseDto.class);
        dto.setTaxRateId(taxRate.getTaxRateId());
        dto.setType(taxRate.getType().name());
        if (taxRate.getEffectiveFrom() != null) {
            dto.setEffectiveFrom(taxRate.getEffectiveFrom().toString());
        }
        if (taxRate.getEffectiveTo() != null) {
            dto.setEffectiveTo(taxRate.getEffectiveTo().toString());
        }
        return dto;
    }
}
