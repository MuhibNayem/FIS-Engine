package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DerivedBalanceService {

    private final JdbcTemplate jdbcTemplate;
    private final AccountRepository accountRepository;

    public long computeBalanceFromJournal(UUID accountId) {
        String sql = """
            SELECT COALESCE(
                SUM(CASE
                    WHEN jl.is_credit = false THEN jl.base_amount
                    ELSE -jl.base_amount
                END), 0
            )
            FROM fis_journal_line jl
            JOIN fis_journal_entry je ON jl.journal_entry_id = je.journal_entry_id
            WHERE jl.account_id = ?
            AND je.status = 'POSTED'
            """;

        Long balance = jdbcTemplate.queryForObject(sql, Long.class, accountId.toString());
        return balance != null ? balance : 0L;
    }

    public void syncAccountBalance(UUID accountId) {
        long derivedBalance = computeBalanceFromJournal(accountId);

        int updated = jdbcTemplate.update("""
            UPDATE fis_account
            SET current_balance = ?, updated_at = NOW()
            WHERE account_id = ?
            """, derivedBalance, accountId.toString());

        if (updated > 0) {
            log.debug("Synced balance for account {}: derived={}", accountId, derivedBalance);
        }
    }

    public void syncAllAccountBalances(UUID tenantId) {
        String sql = """
            UPDATE fis_account a
            SET current_balance = COALESCE((
                SELECT SUM(CASE
                    WHEN jl.is_credit = false THEN jl.base_amount
                    ELSE -jl.base_amount
                END)
                FROM fis_journal_line jl
                JOIN fis_journal_entry je ON jl.journal_entry_id = je.journal_entry_id
                WHERE jl.account_id = a.account_id
                AND je.status = 'POSTED'
            ), 0)
            WHERE a.tenant_id = ?
            """;

        int[] results = jdbcTemplate.batchUpdate(sql, tenantId.toString());
        int totalUpdated = results.length;
        log.info("Synced balances for {} accounts in tenant {}", totalUpdated, tenantId);
    }
}
