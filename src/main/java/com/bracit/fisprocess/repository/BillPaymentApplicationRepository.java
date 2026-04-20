package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BillPaymentApplication} persistence operations.
 */
@Repository
public interface BillPaymentApplicationRepository extends JpaRepository<BillPaymentApplication, UUID> {

    /**
     * Finds all applications for a payment.
     */
    List<BillPaymentApplication> findByPaymentId(UUID paymentId);

    /**
     * Finds all applications for a bill.
     */
    List<BillPaymentApplication> findByBillId(UUID billId);
}
