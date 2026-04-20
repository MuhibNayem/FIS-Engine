package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.ARPayment;
import com.bracit.fisprocess.domain.entity.PaymentApplication;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto;
import com.bracit.fisprocess.dto.request.RecordPaymentRequestDto;
import com.bracit.fisprocess.dto.response.PaymentResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for AR Payment operations.
 */
public interface PaymentService {

    /**
     * Records a new payment from a customer.
     */
    ARPayment recordPayment(UUID tenantId, RecordPaymentRequestDto request, String performedBy);

    /**
     * Applies a payment to one or more invoices — posts journal entries to the GL
     * for each application.
     *
     * @param tenantId     the tenant UUID
     * @param request      the application details
     * @param performedBy  the actor performing the action
     * @return the updated ARPayment
     */
    ARPayment applyPayment(UUID tenantId, ApplyPaymentRequestDto request, String performedBy);

    /**
     * Retrieves a payment by ID, validating tenant ownership.
     */
    ARPayment getPayment(UUID tenantId, UUID paymentId);

    /**
     * Retrieves all applications for a payment.
     */
    List<PaymentApplication> getPaymentApplications(UUID paymentId);

    /**
     * Lists payments for a tenant with optional customer filter.
     */
    Page<PaymentResponseDto> listPayments(
            UUID tenantId,
            UUID customerId,
            Pageable pageable);
}
