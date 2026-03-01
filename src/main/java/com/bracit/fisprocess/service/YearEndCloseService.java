package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.YearEndCloseRequestDto;
import com.bracit.fisprocess.dto.response.YearEndCloseResponseDto;

import java.util.UUID;

/**
 * Service for performing fiscal year-end close operations.
 * <p>
 * The year-end close process:
 * <ol>
 * <li>Validates all accounting periods for the fiscal year are HARD_CLOSED</li>
 * <li>Computes net income (Revenue - Expenses)</li>
 * <li>Generates a closing Journal Entry that zeroes out all P&amp;L accounts
 * and transfers the net amount to Retained Earnings</li>
 * </ol>
 */
public interface YearEndCloseService {

    /**
     * Performs the year-end close for the specified tenant and fiscal year.
     *
     * @param tenantId the tenant UUID
     * @param request  the year-end close parameters
     * @return the result of the year-end close operation
     */
    YearEndCloseResponseDto performYearEndClose(UUID tenantId, YearEndCloseRequestDto request);
}
