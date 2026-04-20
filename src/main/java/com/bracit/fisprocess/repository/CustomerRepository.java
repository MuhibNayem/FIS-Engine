package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Customer;
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
 * Repository for {@link Customer} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Finds a customer by code within a tenant.
     */
    Optional<Customer> findByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Checks if a customer code already exists for a given tenant.
     */
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    /**
     * Lists customers for a tenant with optional search filter.
     */
    @Query("""
            SELECT c FROM Customer c
            WHERE c.tenantId = :tenantId
              AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Customer> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") @Nullable String search,
            Pageable pageable);
}
