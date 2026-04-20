package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.DebitNote;
import com.bracit.fisprocess.domain.enums.DebitNoteStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link DebitNote} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface DebitNoteRepository extends JpaRepository<DebitNote, UUID> {

    /**
     * Lists debit notes for a tenant with optional vendor filter.
     */
    @Query("""
            SELECT d FROM DebitNote d
            WHERE d.tenantId = :tenantId
              AND (:vendorId IS NULL OR d.vendorId = :vendorId)
              AND (:status IS NULL OR d.status = :status)
            ORDER BY d.createdAt DESC
            """)
    Page<DebitNote> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("vendorId") @Nullable UUID vendorId,
            @Param("status") @Nullable DebitNoteStatus status,
            Pageable pageable);
}
