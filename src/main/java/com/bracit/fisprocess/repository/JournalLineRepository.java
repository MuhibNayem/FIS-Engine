package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for journal lines.
 */
public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    List<JournalLine> findByJournalEntry_Id(UUID journalEntryId);

    @Query(value = """
            SELECT
                je.transaction_currency AS transactionCurrency,
                COALESCE(SUM(CASE WHEN jl.is_credit THEN -jl.amount ELSE jl.amount END), 0) AS signedAmountCents,
                COALESCE(SUM(CASE WHEN jl.is_credit THEN -jl.base_amount ELSE jl.base_amount END), 0) AS signedBaseAmountCents
            FROM fis_journal_entry je
            JOIN fis_journal_line jl ON jl.journal_entry_id = je.journal_entry_id
            JOIN fis_account a ON a.account_id = jl.account_id
            WHERE je.tenant_id = :tenantId
              AND je.posted_date BETWEEN :startDate AND :endDate
              AND je.status IN ('POSTED', 'CORRECTION')
              AND je.transaction_currency <> je.base_currency
              AND a.is_active = TRUE
              AND a.account_type IN ('ASSET', 'LIABILITY')
            GROUP BY je.transaction_currency
            """, nativeQuery = true)
    List<JournalExposureView> aggregateExposureByCurrency(
            @Param("tenantId") UUID tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
