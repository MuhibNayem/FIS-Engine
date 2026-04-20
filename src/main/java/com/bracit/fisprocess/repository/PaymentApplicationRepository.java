package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.PaymentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link PaymentApplication} persistence operations.
 */
@Repository
public interface PaymentApplicationRepository extends JpaRepository<PaymentApplication, UUID> {

    /**
     * Finds all applications for a payment.
     */
    List<PaymentApplication> findByPaymentId(UUID paymentId);

    /**
     * Finds all applications for an invoice.
     */
    List<PaymentApplication> findByInvoiceId(UUID invoiceId);
}
