package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.TaxGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TaxGroup} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface TaxGroupRepository extends JpaRepository<TaxGroup, UUID> {

    /**
     * Lists tax groups for a tenant.
     */
    @Query("""
            SELECT g FROM TaxGroup g
            WHERE g.tenantId = :tenantId
            ORDER BY g.createdAt DESC
            """)
    Page<TaxGroup> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Finds all tax groups for a tenant.
     */
    List<TaxGroup> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
