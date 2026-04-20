package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.enums.TaxType;
import com.bracit.fisprocess.domain.entity.TaxRate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TaxRate} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    /**
     * Finds a tax rate by code within a tenant.
     */
    Optional<TaxRate> findByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Checks if a tax rate code already exists for a given tenant.
     */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Lists tax rates for a tenant with optional filters.
     */
    @Query("""
            SELECT t FROM TaxRate t
            WHERE t.tenantId = :tenantId
              AND (:type IS NULL OR t.type = :type)
              AND (:isActive IS NULL OR t.isActive = :isActive)
            ORDER BY t.createdAt DESC
            """)
    Page<TaxRate> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("type") @Nullable TaxType type,
            @Param("isActive") @Nullable Boolean isActive,
            Pageable pageable);

    /**
     * Finds all active tax rates for a list of rate IDs effective on a date.
     */
    @Query("""
            SELECT t FROM TaxRate t
            WHERE t.taxRateId IN :rateIds
              AND t.isActive = true
              AND t.effectiveFrom <= :asOfDate
              AND (t.effectiveTo IS NULL OR t.effectiveTo >= :asOfDate)
            """)
    List<TaxRate> findActiveRatesByIdsAndDate(
            @Param("rateIds") List<UUID> rateIds,
            @Param("asOfDate") LocalDate asOfDate);
}
