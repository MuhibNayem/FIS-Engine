package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.response.ARAgingReportDto;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrator service for AR Aging reports.
 */
public interface AgingService {

    /**
     * Generates an AR aging report as of a given date.
     *
     * @param tenantId   the tenant UUID
     * @param customerId optional customer filter (null = all customers)
     * @param asOfDate   the date to calculate aging against
     * @return the aging report
     */
    ARAgingReportDto getAgingReport(
            UUID tenantId,
            @Nullable UUID customerId,
            LocalDate asOfDate);
}
