package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
