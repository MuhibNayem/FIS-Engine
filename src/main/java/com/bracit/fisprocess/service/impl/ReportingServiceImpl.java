package com.bracit.fisprocess.service.impl;

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
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.ReportingRepository;
import com.bracit.fisprocess.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Implementation of the financial reporting service.
 * <p>
 * All report generation is read-only and runs inside a read-only transaction
 * for snapshot isolation. Reports use base-currency (cents) values throughout.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {

    private final ReportingRepository reportingRepository;
    private final BusinessEntityRepository businessEntityRepository;

    @Override
    public TrialBalanceReportDto generateTrialBalance(UUID tenantId, LocalDate asOfDate) {
        return ReportingStatementOps.generateTrialBalance(
                reportingRepository, businessEntityRepository, tenantId, asOfDate);
    }

    @Override
    public BalanceSheetReportDto generateBalanceSheet(UUID tenantId, LocalDate asOfDate) {
        return ReportingStatementOps.generateBalanceSheet(
                reportingRepository, businessEntityRepository, tenantId, asOfDate);
    }

    @Override
    public IncomeStatementReportDto generateIncomeStatement(UUID tenantId, LocalDate fromDate, LocalDate toDate) {
        return ReportingStatementOps.generateIncomeStatement(
                reportingRepository, businessEntityRepository, tenantId, fromDate, toDate);
    }

    @Override
    public GeneralLedgerReportDto generateGeneralLedger(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerOps.generateGeneralLedger(
                reportingRepository, businessEntityRepository, tenantId, accountCode, fromDate, toDate);
    }

    @Override
    public CashFlowReportDto generateCashFlow(UUID tenantId, LocalDate fromDate, LocalDate toDate) {
        return ReportingRiskOps.generateCashFlow(
                reportingRepository, businessEntityRepository, tenantId, fromDate, toDate);
    }

    @Override
    public AccountActivityReportDto generateAccountActivity(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerOps.generateAccountActivity(
                reportingRepository, businessEntityRepository, tenantId, accountCode, fromDate, toDate);
    }

    @Override
    public JournalRegisterReportDto generateJournalRegister(UUID tenantId, LocalDate fromDate,
            LocalDate toDate, int page, int size) {
        return ReportingLedgerOps.generateJournalRegister(
                reportingRepository, businessEntityRepository, tenantId, fromDate, toDate, page, size);
    }

    @Override
    public DimensionSummaryReportDto generateDimensionSummary(UUID tenantId, String dimensionKey,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerOps.generateDimensionSummary(
                reportingRepository, businessEntityRepository, tenantId, dimensionKey, fromDate, toDate);
    }

    @Override
    public FxExposureReportDto generateFxExposure(UUID tenantId, LocalDate asOfDate) {
        return ReportingRiskOps.generateFxExposure(
                reportingRepository, businessEntityRepository, tenantId, asOfDate);
    }

    @Override
    public AgingReportDto generateAging(UUID tenantId, String accountType, LocalDate asOfDate) {
        return ReportingRiskOps.generateAging(
                reportingRepository, businessEntityRepository, tenantId, accountType, asOfDate);
    }
}
