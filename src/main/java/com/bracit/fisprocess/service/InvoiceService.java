package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.InvoiceLine;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateCustomerRequestDto;
import com.bracit.fisprocess.dto.request.CreateInvoiceRequestDto;
import com.bracit.fisprocess.dto.response.InvoiceResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for AR Invoice and Customer operations.
 */
public interface InvoiceService {

    /**
     * Creates a new AR Customer.
     */
    Customer createCustomer(UUID tenantId, CreateCustomerRequestDto request);

    /**
     * Creates a new draft Invoice.
     */
    Invoice createInvoice(UUID tenantId, CreateInvoiceRequestDto request);

    /**
     * Finalizes a draft invoice — posts a journal entry to the GL and transitions
     * status to POSTED.
     *
     * @param tenantId     the tenant UUID
     * @param invoiceId    the invoice UUID
     * @param performedBy  the actor performing the action
     * @return the finalized Invoice
     */
    Invoice finalizeInvoice(UUID tenantId, UUID invoiceId, String performedBy);

    /**
     * Voids a draft invoice. Cannot void a finalized invoice that has payments.
     */
    Invoice voidInvoice(UUID tenantId, UUID invoiceId, String performedBy);

    /**
     * Retrieves an invoice by ID, validating tenant ownership.
     */
    Invoice getInvoice(UUID tenantId, UUID invoiceId);

    /**
     * Retrieves a customer by ID, validating tenant ownership.
     */
    Customer getCustomer(UUID tenantId, UUID customerId);

    /**
     * Lists customers for a tenant with optional search filter.
     */
    Page<Customer> listCustomers(
            UUID tenantId,
            @Nullable String search,
            Pageable pageable);

    /**
     * Lists invoices for a tenant with optional filters.
     */
    Page<InvoiceResponseDto> listInvoices(
            UUID tenantId,
            @Nullable UUID customerId,
            @Nullable InvoiceStatus status,
            Pageable pageable);

    /**
     * Retrieves all line items for an invoice.
     */
    List<InvoiceLine> getInvoiceLines(UUID invoiceId);
}
