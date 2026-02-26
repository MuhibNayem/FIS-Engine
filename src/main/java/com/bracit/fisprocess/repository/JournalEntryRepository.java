package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalEntry;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for journal entries with hash chain support and filtered queries.
 */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByTenantIdAndId(UUID tenantId, UUID journalEntryId);

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    Optional<JournalEntry> findWithLinesByTenantIdAndId(UUID tenantId, UUID journalEntryId);

    Optional<JournalEntry> findByTenantIdAndEventId(UUID tenantId, String eventId);

    /**
     * Returns the most recently created journal entry for hash chain computation.
     * Used to retrieve the previous hash when posting a new entry.
     */
    Optional<JournalEntry> findTopByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    boolean existsByReversalOfId(UUID reversalOfId);

    boolean existsByTenantIdAndEventId(UUID tenantId, String eventId);

    long countByTenantIdAndEventId(UUID tenantId, String eventId);

    /**
     * Filtered and paginated query for journal entries.
     * All filter params are nullable — null values are ignored.
     */
    @Query("""
            SELECT je FROM JournalEntry je
            WHERE je.tenantId = :tenantId
              AND (:postedDateFrom IS NULL OR je.postedDate >= :postedDateFrom)
              AND (:postedDateTo IS NULL OR je.postedDate <= :postedDateTo)
              AND (:accountCode IS NULL OR EXISTS (
                    SELECT 1 FROM JournalLine jl
                    WHERE jl.journalEntry = je
                      AND jl.account.code = :accountCode
              ))
              AND (:status IS NULL OR je.status = :status)
              AND (:referenceId IS NULL OR je.referenceId = :referenceId)
            ORDER BY je.createdAt DESC
            """)
    Page<JournalEntry> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("postedDateFrom") @Nullable LocalDate postedDateFrom,
            @Param("postedDateTo") @Nullable LocalDate postedDateTo,
            @Param("accountCode") @Nullable String accountCode,
            @Param("status") @Nullable JournalStatus status,
            @Param("referenceId") @Nullable String referenceId,
            Pageable pageable);
}
