package com.bracit.fisprocess.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ReportingLedgerQueries {

    private ReportingLedgerQueries() {
    }

    static List<Map<String, Object>> findGeneralLedgerEntries(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            String accountCode,
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    je.journal_entry_id  AS journal_entry_id,
                    je.sequence_number   AS sequence_number,
                    je.posted_date       AS posted_date,
                    je.description       AS description,
                    CASE WHEN NOT jl.is_credit THEN jl.base_amount ELSE 0 END AS debit_amount,
                    CASE WHEN jl.is_credit THEN jl.base_amount ELSE 0 END     AS credit_amount
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                JOIN fis_account a ON a.account_id = jl.account_id
                WHERE a.tenant_id = ?
                  AND a.code = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                ORDER BY je.effective_date, je.sequence_number
                """, tenantId, accountCode, fromDate, toDate);
    }

    static long computeOpeningBalance(JdbcTemplate jdbcTemplate, UUID tenantId, String accountCode, LocalDate beforeDate) {
        Long result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(
                    CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                ), 0)
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                JOIN fis_account a ON a.account_id = jl.account_id
                WHERE a.tenant_id = ?
                  AND a.code = ?
                  AND je.effective_date < ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                """, Long.class, tenantId, accountCode, beforeDate);
        return result != null ? result : 0L;
    }

    static Map<String, Object> findAccountActivity(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            String accountCode,
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbcTemplate.queryForMap("""
                SELECT
                    COALESCE(SUM(CASE WHEN NOT jl.is_credit THEN jl.base_amount ELSE 0 END), 0) AS total_debits,
                    COALESCE(SUM(CASE WHEN jl.is_credit THEN jl.base_amount ELSE 0 END), 0)     AS total_credits,
                    COUNT(DISTINCT je.journal_entry_id) AS transaction_count
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                JOIN fis_account a ON a.account_id = jl.account_id
                WHERE a.tenant_id = ?
                  AND a.code = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                """, tenantId, accountCode, fromDate, toDate);
    }

    static List<Map<String, Object>> findJournalRegister(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            LocalDate fromDate,
            LocalDate toDate,
            int offset,
            int limit) {
        return jdbcTemplate.queryForList("""
                SELECT
                    je.journal_entry_id  AS journal_entry_id,
                    je.sequence_number   AS sequence_number,
                    je.posted_date       AS posted_date,
                    je.description       AS description,
                    je.status            AS status,
                    je.created_by        AS created_by,
                    COALESCE(SUM(CASE WHEN NOT jl.is_credit THEN jl.base_amount ELSE 0 END), 0) AS total_debits,
                    COALESCE(SUM(CASE WHEN jl.is_credit THEN jl.base_amount ELSE 0 END), 0)     AS total_credits
                FROM fis_journal_entry je
                LEFT JOIN fis_journal_line jl ON jl.journal_entry_id = je.journal_entry_id
                WHERE je.tenant_id = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION', 'REVERSED')
                GROUP BY je.journal_entry_id, je.sequence_number, je.posted_date,
                         je.description, je.status, je.created_by
                ORDER BY je.effective_date, je.sequence_number
                LIMIT ? OFFSET ?
                """, tenantId, fromDate, toDate, limit, offset);
    }

    static long countJournalRegister(JdbcTemplate jdbcTemplate, UUID tenantId, LocalDate fromDate, LocalDate toDate) {
        Long result = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fis_journal_entry je
                WHERE je.tenant_id = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION', 'REVERSED')
                """, Long.class, tenantId, fromDate, toDate);
        return result != null ? result : 0L;
    }

    static List<Map<String, Object>> findDimensionSummary(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            String dimensionKey,
            LocalDate fromDate,
            LocalDate toDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    jl.dimensions ->> ? AS dimension_value,
                    COALESCE(SUM(CASE WHEN NOT jl.is_credit THEN jl.base_amount ELSE 0 END), 0) AS debit_total,
                    COALESCE(SUM(CASE WHEN jl.is_credit THEN jl.base_amount ELSE 0 END), 0)     AS credit_total,
                    COALESCE(SUM(CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END), 0) AS net_amount
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                WHERE je.tenant_id = ?
                  AND je.effective_date BETWEEN ? AND ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                  AND jl.dimensions IS NOT NULL
                  AND jl.dimensions ->> ? IS NOT NULL
                GROUP BY dimension_value
                ORDER BY net_amount DESC
                """, dimensionKey, tenantId, fromDate, toDate, dimensionKey);
    }

    static Optional<String> findAccountName(JdbcTemplate jdbcTemplate, UUID tenantId, String accountCode) {
        List<String> names = jdbcTemplate.queryForList("""
                SELECT name
                FROM fis_account
                WHERE tenant_id = ? AND code = ?
                LIMIT 1
                """, String.class, tenantId, accountCode);
        return names.isEmpty() ? Optional.empty() : Optional.ofNullable(names.getFirst());
    }

    static boolean accountExists(JdbcTemplate jdbcTemplate, UUID tenantId, String accountCode) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fis_account
                WHERE tenant_id = ? AND code = ?
                """, Integer.class, tenantId, accountCode);
        return count != null && count > 0;
    }
}
