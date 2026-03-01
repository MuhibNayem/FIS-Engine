package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.response.AccountActivityReportDto;
import com.bracit.fisprocess.dto.response.AgingReportDto;
import com.bracit.fisprocess.dto.response.BalanceSheetReportDto;
import com.bracit.fisprocess.dto.response.CashFlowReportDto;
import com.bracit.fisprocess.dto.response.DimensionSummaryReportDto;
import com.bracit.fisprocess.dto.response.FxExposureReportDto;
import com.bracit.fisprocess.dto.response.GeneralLedgerReportDto;
import com.bracit.fisprocess.dto.response.IncomeStatementReportDto;
import com.bracit.fisprocess.dto.response.JournalRegisterReportDto;
import com.bracit.fisprocess.dto.response.TrialBalanceReportDto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service interface for generating financial reports.
 */
public interface ReportingService {

    TrialBalanceReportDto generateTrialBalance(UUID tenantId, LocalDate asOfDate);

    BalanceSheetReportDto generateBalanceSheet(UUID tenantId, LocalDate asOfDate);

    IncomeStatementReportDto generateIncomeStatement(UUID tenantId, LocalDate fromDate, LocalDate toDate);

    GeneralLedgerReportDto generateGeneralLedger(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate);

    CashFlowReportDto generateCashFlow(UUID tenantId, LocalDate fromDate, LocalDate toDate);

    AccountActivityReportDto generateAccountActivity(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate);

    JournalRegisterReportDto generateJournalRegister(UUID tenantId, LocalDate fromDate,
            LocalDate toDate, int page, int size);

    DimensionSummaryReportDto generateDimensionSummary(UUID tenantId, String dimensionKey,
            LocalDate fromDate, LocalDate toDate);

    FxExposureReportDto generateFxExposure(UUID tenantId, LocalDate asOfDate);

    AgingReportDto generateAging(UUID tenantId, String accountType, LocalDate asOfDate);
}
