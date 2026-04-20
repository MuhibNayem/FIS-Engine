package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.TaxJurisdiction;
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
 * Repository for {@link TaxJurisdiction} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface TaxJurisdictionRepository extends JpaRepository<TaxJurisdiction, UUID> {

    /**
     * Lists tax jurisdictions for a tenant.
     */
    @Query("""
            SELECT j FROM TaxJurisdiction j
            WHERE j.tenantId = :tenantId
            ORDER BY j.name ASC
            """)
    Page<TaxJurisdiction> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Finds all tax jurisdictions for a tenant.
     */
    List<TaxJurisdiction> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
