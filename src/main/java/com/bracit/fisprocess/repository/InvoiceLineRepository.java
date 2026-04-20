package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link InvoiceLine} persistence operations.
 */
@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    /**
     * Finds all lines for an invoice, ordered by sort order.
     */
    List<InvoiceLine> findByInvoiceIdOrderBySortOrder(UUID invoiceId);
}
