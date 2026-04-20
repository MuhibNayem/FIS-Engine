package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.Reconciliation;
import com.bracit.fisprocess.domain.enums.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface ReconciliationRepository extends JpaRepository<Reconciliation, UUID> {
    Optional<Reconciliation> findByTenantIdAndId(UUID tenantId, UUID id);
    @Query("SELECT r FROM Reconciliation r WHERE r.tenantId = :tenantId AND (:bankAccountId IS NULL OR r.bankAccountId = :bankAccountId) AND (:status IS NULL OR r.status = :status)")
    Page<Reconciliation> findByTenantIdWithFilters(@Param("tenantId") UUID tenantId, @Param("bankAccountId") UUID bankAccountId, @Param("status") ReconciliationStatus status, Pageable pageable);
}
