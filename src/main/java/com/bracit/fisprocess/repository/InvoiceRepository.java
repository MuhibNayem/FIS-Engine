package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
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
 * Repository for {@link Invoice} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /**
     * Finds an invoice by ID within a tenant.
     */
    Optional<Invoice> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Finds an invoice by invoice number within a tenant.
     */
    Optional<Invoice> findByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);

    /**
     * Lists invoices for a tenant with optional filters.
     */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.tenantId = :tenantId
              AND (:customerId IS NULL OR i.customerId = :customerId)
              AND (:status IS NULL OR i.status = :status)
            ORDER BY i.createdAt DESC
            """)
    Page<Invoice> findByTenantIdWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("customerId") @Nullable UUID customerId,
            @Param("status") @Nullable InvoiceStatus status,
            Pageable pageable);

    /**
     * Finds invoices by customer and status list.
     */
    List<Invoice> findByTenantIdAndCustomerIdAndStatusIn(
            UUID tenantId, UUID customerId, List<InvoiceStatus> statuses);

    /**
     * Checks if an invoice number already exists for a tenant.
     */
    boolean existsByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);
}
