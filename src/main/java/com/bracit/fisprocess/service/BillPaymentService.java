package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.BillPayment;
import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto;
import com.bracit.fisprocess.dto.request.RecordBillPaymentRequestDto;
import com.bracit.fisprocess.dto.response.BillPaymentResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for AP Bill Payment operations.
 */
public interface BillPaymentService {

    /**
     * Records a new payment to a vendor.
     */
    BillPayment recordPayment(UUID tenantId, RecordBillPaymentRequestDto request, String performedBy);

    /**
     * Applies a payment to one or more bills — posts journal entries to the GL
     * for each application.
     *
     * @param tenantId     the tenant UUID
     * @param request      the application details
     * @param performedBy  the actor performing the action
     * @return the updated BillPayment
     */
    BillPayment applyPayment(UUID tenantId, ApplyBillPaymentRequestDto request, String performedBy);

    /**
     * Retrieves a payment by ID, validating tenant ownership.
     */
    BillPayment getPayment(UUID tenantId, UUID paymentId);

    /**
     * Retrieves all applications for a payment.
     */
    List<BillPaymentApplication> getPaymentApplications(UUID paymentId);

    /**
     * Lists payments for a tenant with optional vendor filter.
     */
    Page<BillPaymentResponseDto> listPayments(
            UUID tenantId,
            @Nullable UUID vendorId,
            Pageable pageable);
}
