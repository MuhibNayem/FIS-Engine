package com.bracit.fisprocess.controller;

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
import com.bracit.fisprocess.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for financial reporting endpoints.
 * <p>
 * All endpoints are read-only (GET) and return tenant-scoped reports.
 * Access is governed by the existing RBAC rules which allow
 * {@code FIS_READER}, {@code FIS_ACCOUNTANT}, and {@code FIS_ADMIN} roles
 * for all GET requests under {@code /v1/**}.
 */
@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
public class FinancialReportingController {

    private final ReportingService reportingService;

    /**
     * Trial Balance — verifies Σ Debits = Σ Credits across all accounts.
     */
    @GetMapping("/trial-balance")
    public ResponseEntity<TrialBalanceReportDto> trialBalance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(reportingService.generateTrialBalance(tenantId, asOfDate));
    }

    /**
     * Balance Sheet — Assets = Liabilities + Equity at a specific date.
     */
    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetReportDto> balanceSheet(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(reportingService.generateBalanceSheet(tenantId, asOfDate));
    }

    /**
     * Income Statement (P&amp;L) — Revenue − Expenses over a date range.
     */
    @GetMapping("/income-statement")
    public ResponseEntity<IncomeStatementReportDto> incomeStatement(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportingService.generateIncomeStatement(tenantId, fromDate, toDate));
    }

    /**
     * General Ledger Detail — all transactions for a specific account with running
     * balance.
     */
    @GetMapping("/general-ledger/{accountCode}")
    public ResponseEntity<GeneralLedgerReportDto> generalLedger(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String accountCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportingService.generateGeneralLedger(tenantId, accountCode, fromDate, toDate));
    }

    /**
     * Cash Flow Statement — cash inflows/outflows classified by activity type.
     */
    @GetMapping("/cash-flow")
    public ResponseEntity<CashFlowReportDto> cashFlow(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportingService.generateCashFlow(tenantId, fromDate, toDate));
    }

    /**
     * Account Activity — summarized debit/credit activity for a specific account.
     */
    @GetMapping("/account-activity/{accountCode}")
    public ResponseEntity<AccountActivityReportDto> accountActivity(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String accountCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportingService.generateAccountActivity(tenantId, accountCode, fromDate, toDate));
    }

    /**
     * Journal Register — chronological listing of all journal entries with totals.
     */
    @GetMapping("/journal-register")
    public ResponseEntity<JournalRegisterReportDto> journalRegister(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(reportingService.generateJournalRegister(tenantId, fromDate, toDate, page, size));
    }

    /**
     * Dimension Summary — aggregate income/expense by dimension tags.
     */
    @GetMapping("/dimension-summary")
    public ResponseEntity<DimensionSummaryReportDto> dimensionSummary(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam String dimensionKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(reportingService.generateDimensionSummary(tenantId, dimensionKey, fromDate, toDate));
    }

    /**
     * FX Exposure — open foreign-currency positions with unrealized gain/loss.
     */
    @GetMapping("/fx-exposure")
    public ResponseEntity<FxExposureReportDto> fxExposure(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(reportingService.generateFxExposure(tenantId, asOfDate));
    }

    /**
     * Aging Analysis — outstanding balances bucketed by age.
     */
    @GetMapping("/aging")
    public ResponseEntity<AgingReportDto> aging(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam String accountType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(reportingService.generateAging(tenantId, accountType, asOfDate));
    }
}
