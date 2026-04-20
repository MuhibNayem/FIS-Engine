package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.domain.entity.Bill;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Bill} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    /**
     * Finds a bill by ID within a tenant.
     */
    Optional<Bill> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Finds a bill by bill number within a tenant.
     */
    Optional<Bill> findByTenantIdAndBillNumber(UUID tenantId, String billNumber);

    /**
     * Lists bills for a tenant with optional filters.
     */
    @Query("""
            SELECT b FROM Bill b
            WHERE b.tenantId = :tenantId
              AND (:vendorId IS NULL OR b.vendorId = :vendorId)
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.createdAt DESC
            """)
    Page<Bill> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("vendorId") @Nullable UUID vendorId,
            @Param("status") @Nullable BillStatus status,
            Pageable pageable);

    /**
     * Finds bills by vendor and status list.
     */
    List<Bill> findByTenantIdAndVendorIdAndStatusIn(
            UUID tenantId, UUID vendorId, List<BillStatus> statuses);

    /**
     * Checks if a bill number already exists for a tenant.
     */
    boolean existsByTenantIdAndBillNumber(UUID tenantId, String billNumber);
}
