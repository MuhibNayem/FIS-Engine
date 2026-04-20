package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.BillPayment;
import com.bracit.fisprocess.domain.enums.BillPaymentStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link BillPayment} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    /**
     * Lists payments for a tenant with optional vendor filter.
     */
    @Query("""
            SELECT p FROM BillPayment p
            WHERE p.tenantId = :tenantId
              AND (:vendorId IS NULL OR p.vendorId = :vendorId)
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC
            """)
    Page<BillPayment> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("vendorId") @Nullable UUID vendorId,
            @Param("status") @Nullable BillPaymentStatus status,
            Pageable pageable);
}
