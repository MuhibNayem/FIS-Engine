package com.bracit.fisprocess.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ReportingCoreQueries {

    private ReportingCoreQueries() {
    }

    static List<Map<String, Object>> findTrialBalanceLines(JdbcTemplate jdbcTemplate, UUID tenantId, LocalDate asOfDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    a.code            AS account_code,
                    a.name            AS account_name,
                    a.account_type    AS account_type,
                    COALESCE(SUM(CASE WHEN NOT jl.is_credit THEN jl.base_amount ELSE 0 END), 0) AS total_debits,
                    COALESCE(SUM(CASE WHEN jl.is_credit THEN jl.base_amount ELSE 0 END), 0)     AS total_credits
                FROM fis_account a
                LEFT JOIN (
                    SELECT jl2.account_id, jl2.is_credit, jl2.base_amount
                    FROM fis_journal_line jl2
                    JOIN fis_journal_entry je2 ON je2.journal_entry_id = jl2.journal_entry_id
                    WHERE je2.effective_date <= ?
                      AND je2.status IN ('POSTED', 'CORRECTION')
                      AND je2.tenant_id = ?
                ) jl ON jl.account_id = a.account_id
                WHERE a.tenant_id = ?
                  AND a.is_active = TRUE
                GROUP BY a.code, a.name, a.account_type
                ORDER BY a.account_type, a.code
                """, asOfDate, tenantId, tenantId);
    }

    static List<Map<String, Object>> findBalanceSheetAccounts(JdbcTemplate jdbcTemplate, UUID tenantId, LocalDate asOfDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    a.code            AS account_code,
                    a.name            AS account_name,
                    a.account_type    AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                    ), 0) AS net_balance
                FROM fis_account a
                LEFT JOIN (
                    SELECT jl2.account_id, jl2.is_credit, jl2.base_amount
                    FROM fis_journal_line jl2
                    JOIN fis_journal_entry je2 ON je2.journal_entry_id = jl2.journal_entry_id
                    WHERE je2.effective_date <= ?
                      AND je2.status IN ('POSTED', 'CORRECTION')
                      AND je2.tenant_id = ?
                ) jl ON jl.account_id = a.account_id
                WHERE a.tenant_id = ?
                  AND a.is_active = TRUE
                  AND a.account_type IN ('ASSET', 'LIABILITY', 'EQUITY')
                GROUP BY a.code, a.name, a.account_type
                ORDER BY a.account_type, a.code
                """, asOfDate, tenantId, tenantId);
    }

    static List<Map<String, Object>> findIncomeStatementAccounts(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    a.code            AS account_code,
                    a.name            AS account_name,
                    a.account_type    AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                    ), 0) AS net_amount
                FROM fis_account a
                JOIN fis_journal_line jl ON jl.account_id = a.account_id
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                WHERE a.tenant_id = ?
                  AND a.is_active = TRUE
                  AND a.account_type IN ('REVENUE', 'EXPENSE')
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                GROUP BY a.code, a.name, a.account_type
                ORDER BY a.account_type, a.code
                """, tenantId, fromDate, toDate);
    }

    static List<Map<String, Object>> findNetMovementByAccountType(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    a.account_type AS account_type,
                    a.code         AS account_code,
                    a.name         AS account_name,
                    COALESCE(SUM(
                        CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                    ), 0) AS net_movement
                FROM fis_account a
                JOIN fis_journal_line jl ON jl.account_id = a.account_id
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                WHERE a.tenant_id = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                GROUP BY a.account_type, a.code, a.name
                ORDER BY a.account_type, a.code
                """, tenantId, fromDate, toDate);
    }

    static long findCashBalance(JdbcTemplate jdbcTemplate, UUID tenantId, LocalDate asOfDate) {
        Long result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                ), 0)
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                JOIN fis_account a ON a.account_id = jl.account_id
                WHERE a.tenant_id = ?
                  AND a.account_type = 'ASSET'
                  AND je.effective_date <= ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                """, Long.class, tenantId, asOfDate);
        return result != null ? result : 0L;
    }
}
