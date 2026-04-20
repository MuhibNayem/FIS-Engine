package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.ARPayment;
import com.bracit.fisprocess.domain.enums.PaymentStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link ARPayment} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface ARPaymentRepository extends JpaRepository<ARPayment, UUID> {

    /**
     * Lists payments for a tenant with optional customer filter.
     */
    @Query("""
            SELECT p FROM ARPayment p
            WHERE p.tenantId = :tenantId
              AND (:customerId IS NULL OR p.customerId = :customerId)
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC
            """)
    Page<ARPayment> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("customerId") @Nullable UUID customerId,
            @Param("status") @Nullable PaymentStatus status,
            Pageable pageable);
}
