package com.bracit.fisprocess.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only reporting query facade.
 * Delegates to focused query modules to keep classes small and maintainable.
 */
@Repository
@RequiredArgsConstructor
public class ReportingRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> findTrialBalanceLines(UUID tenantId, LocalDate asOfDate) {
        return ReportingCoreQueries.findTrialBalanceLines(jdbcTemplate, tenantId, asOfDate);
    }

    public List<Map<String, Object>> findBalanceSheetAccounts(UUID tenantId, LocalDate asOfDate) {
        return ReportingCoreQueries.findBalanceSheetAccounts(jdbcTemplate, tenantId, asOfDate);
    }

    public List<Map<String, Object>> findIncomeStatementAccounts(UUID tenantId, LocalDate fromDate, LocalDate toDate) {
        return ReportingCoreQueries.findIncomeStatementAccounts(jdbcTemplate, tenantId, fromDate, toDate);
    }

    public List<Map<String, Object>> findGeneralLedgerEntries(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerQueries.findGeneralLedgerEntries(jdbcTemplate, tenantId, accountCode, fromDate, toDate);
    }

    public long computeOpeningBalance(UUID tenantId, String accountCode, LocalDate beforeDate) {
        return ReportingLedgerQueries.computeOpeningBalance(jdbcTemplate, tenantId, accountCode, beforeDate);
    }

    public Map<String, Object> findAccountActivity(UUID tenantId, String accountCode,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerQueries.findAccountActivity(jdbcTemplate, tenantId, accountCode, fromDate, toDate);
    }

    public List<Map<String, Object>> findJournalRegister(UUID tenantId, LocalDate fromDate,
            LocalDate toDate, int offset, int limit) {
        return ReportingLedgerQueries.findJournalRegister(jdbcTemplate, tenantId, fromDate, toDate, offset, limit);
    }

    public long countJournalRegister(UUID tenantId, LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerQueries.countJournalRegister(jdbcTemplate, tenantId, fromDate, toDate);
    }

    public List<Map<String, Object>> findDimensionSummary(UUID tenantId, String dimensionKey,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingLedgerQueries.findDimensionSummary(jdbcTemplate, tenantId, dimensionKey, fromDate, toDate);
    }

    public List<Map<String, Object>> findFxExposure(UUID tenantId, LocalDate asOfDate) {
        return ReportingRiskQueries.findFxExposure(jdbcTemplate, tenantId, asOfDate);
    }

    public BigDecimal findLatestRate(UUID tenantId, String sourceCurrency, String targetCurrency) {
        return ReportingRiskQueries.findLatestRate(jdbcTemplate, tenantId, sourceCurrency, targetCurrency);
    }

    public List<Map<String, Object>> findAgingBuckets(UUID tenantId, String accountType, LocalDate asOfDate) {
        return ReportingRiskQueries.findAgingBuckets(jdbcTemplate, tenantId, accountType, asOfDate);
    }

    public List<Map<String, Object>> findNetMovementByAccountType(UUID tenantId,
            LocalDate fromDate, LocalDate toDate) {
        return ReportingCoreQueries.findNetMovementByAccountType(jdbcTemplate, tenantId, fromDate, toDate);
    }

    public long findCashBalance(UUID tenantId, LocalDate asOfDate) {
        return ReportingCoreQueries.findCashBalance(jdbcTemplate, tenantId, asOfDate);
    }

    public Optional<String> findAccountName(UUID tenantId, String accountCode) {
        return ReportingLedgerQueries.findAccountName(jdbcTemplate, tenantId, accountCode);
    }

    public boolean accountExists(UUID tenantId, String accountCode) {
        return ReportingLedgerQueries.accountExists(jdbcTemplate, tenantId, accountCode);
    }
}
