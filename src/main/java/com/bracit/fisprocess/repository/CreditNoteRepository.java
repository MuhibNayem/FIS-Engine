package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.CreditNote;
import com.bracit.fisprocess.domain.enums.CreditNoteStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link CreditNote} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    /**
     * Lists credit notes for a tenant with optional customer filter.
     */
    @Query("""
            SELECT cn FROM CreditNote cn
            WHERE cn.tenantId = :tenantId
              AND (:customerId IS NULL OR cn.customerId = :customerId)
              AND (:status IS NULL OR cn.status = :status)
            ORDER BY cn.createdAt DESC
            """)
    Page<CreditNote> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("customerId") @Nullable UUID customerId,
            @Param("status") @Nullable CreditNoteStatus status,
            Pageable pageable);
}
