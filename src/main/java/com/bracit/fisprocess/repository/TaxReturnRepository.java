package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.TaxReturn;
import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TaxReturn} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface TaxReturnRepository extends JpaRepository<TaxReturn, UUID> {

    /**
     * Finds a tax return by ID within a tenant.
     */
    Optional<TaxReturn> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Finds a tax return by jurisdiction and period.
     */
    Optional<TaxReturn> findByTenantIdAndJurisdictionIdAndPeriod(
            UUID tenantId, UUID jurisdictionId, YearMonth period);

    /**
     * Lists tax returns for a tenant with optional filters.
     */
    @Query("""
            SELECT tr FROM TaxReturn tr
            WHERE tr.tenantId = :tenantId
              AND (:jurisdictionId IS NULL OR tr.jurisdictionId = :jurisdictionId)
              AND (:status IS NULL OR tr.status = :status)
            ORDER BY tr.createdAt DESC
            """)
    Page<TaxReturn> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("jurisdictionId") @Nullable UUID jurisdictionId,
            @Param("status") @Nullable TaxReturnStatus status,
            Pageable pageable);
}
