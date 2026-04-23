package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link BusinessEntity} (tenant) persistence operations.
 */
@Repository
public interface BusinessEntityRepository extends JpaRepository<BusinessEntity, UUID> {

    /**
     * Finds an active Business Entity by its tenant ID.
     *
     * @param tenantId the tenant UUID
     * @return the active BusinessEntity, or empty if not found or inactive
     */
    Optional<BusinessEntity> findByTenantIdAndIsActiveTrue(UUID tenantId);

    /**
     * Acquires a transaction-scoped tenant row lock for serializing tenant-critical
     * operations (e.g., hash chain sequencing).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BusinessEntity b WHERE b.tenantId = :tenantId")
    Optional<BusinessEntity> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    /**
     * Counts all accounts belonging to a tenant.
     * Used by balance reconciliation to track progress.
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.tenantId = :tenantId")
    long countAccountsByTenantId(@Param("tenantId") UUID tenantId);

    /**
     * Returns all tenant IDs in the system.
     * Used by batch jobs that need to iterate across all tenants.
     */
    @Query("SELECT b.tenantId FROM BusinessEntity b")
    List<UUID> findAllIds();
}
