package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.dto.response.ARAgingReportDto;
import com.bracit.fisprocess.service.AgingService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for AR Aging reports.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ar/reports/aging")
@RequiredArgsConstructor
@ApiVersion(1)
public class AgingReportController {

    private final AgingService agingService;

    /**
     * Generates an AR aging report as of a given date.
     *
     * @param tenantId   the tenant UUID
     * @param asOfDate   the date to calculate aging against
     * @param customerId optional customer filter
     * @return 200 OK with the aging report
     */
    @GetMapping
    public ResponseEntity<ARAgingReportDto> getAgingReport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) @Nullable UUID customerId) {
        ARAgingReportDto report = agingService.getAgingReport(tenantId, customerId, asOfDate);
        return ResponseEntity.ok(report);
    }
}
