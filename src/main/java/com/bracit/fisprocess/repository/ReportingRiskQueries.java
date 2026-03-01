package com.bracit.fisprocess.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ReportingRiskQueries {

    private ReportingRiskQueries() {
    }

    static List<Map<String, Object>> findFxExposure(JdbcTemplate jdbcTemplate, UUID tenantId, LocalDate asOfDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    a.currency_code                    AS currency,
                    a.account_type                     AS account_type,
                    COALESCE(SUM(
                        CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END
                    ), 0) AS net_balance
                FROM fis_account a
                JOIN fis_journal_line jl ON jl.account_id = a.account_id
                JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                WHERE a.tenant_id = ?
                  AND je.effective_date <= ?
                  AND je.status IN ('POSTED', 'CORRECTION')
                  AND a.account_type IN ('ASSET', 'LIABILITY')
                  AND a.currency_code <> (
                      SELECT base_currency FROM fis_business_entity WHERE tenant_id = ?
                  )
                GROUP BY a.currency_code, a.account_type
                ORDER BY a.currency_code
                """, tenantId, asOfDate, tenantId);
    }

    static BigDecimal findLatestRate(JdbcTemplate jdbcTemplate, UUID tenantId, String sourceCurrency, String targetCurrency) {
        List<BigDecimal> rates = jdbcTemplate.queryForList("""
                SELECT rate
                FROM fis_exchange_rate
                WHERE tenant_id = ?
                  AND source_currency = ?
                  AND target_currency = ?
                ORDER BY effective_date DESC
                LIMIT 1
                """, BigDecimal.class, tenantId, sourceCurrency, targetCurrency);
        return rates.isEmpty() ? BigDecimal.ONE : rates.getFirst();
    }

    static List<Map<String, Object>> findAgingBuckets(JdbcTemplate jdbcTemplate, UUID tenantId, String accountType, LocalDate asOfDate) {
        return jdbcTemplate.queryForList("""
                SELECT
                    CASE
                        WHEN day_age BETWEEN 0 AND 30  THEN '0-30 days'
                        WHEN day_age BETWEEN 31 AND 60 THEN '31-60 days'
                        WHEN day_age BETWEEN 61 AND 90 THEN '61-90 days'
                        ELSE '90+ days'
                    END AS bucket_label,
                    SUM(signed_amount) AS amount_cents,
                    COUNT(DISTINCT journal_entry_id) AS entry_count
                FROM (
                    SELECT
                        je.journal_entry_id,
                        (? - je.effective_date) AS day_age,
                        CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END AS signed_amount
                    FROM fis_journal_line jl
                    JOIN fis_journal_entry je ON je.journal_entry_id = jl.journal_entry_id
                    JOIN fis_account a ON a.account_id = jl.account_id
                    WHERE a.tenant_id = ?
                      AND a.account_type = ?
                      AND je.effective_date <= ?
                      AND je.status IN ('POSTED', 'CORRECTION')
                ) sub
                GROUP BY 1
                ORDER BY MIN(day_age)
                """, asOfDate, tenantId, accountType, asOfDate);
    }
}
