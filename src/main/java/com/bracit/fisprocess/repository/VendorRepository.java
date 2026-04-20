package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Vendor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Vendor} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    /**
     * Finds a vendor by code within a tenant.
     */
    Optional<Vendor> findByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Checks if a vendor code already exists for a given tenant.
     */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Lists vendors for a tenant with optional search filter.
     */
    @Query("""
            SELECT v FROM Vendor v
            WHERE v.tenantId = :tenantId
              AND (:search IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(v.code) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Vendor> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") @Nullable String search,
            Pageable pageable);
}
