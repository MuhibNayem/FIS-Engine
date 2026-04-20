package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillLine;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.dto.request.CreateBillRequestDto;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.dto.response.BillResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for AP Bill and Vendor operations.
 */
public interface BillService {

    /**
     * Creates a new AP Vendor.
     */
    Vendor createVendor(UUID tenantId, CreateVendorRequestDto request);

    /**
     * Creates a new draft Bill.
     */
    Bill createBill(UUID tenantId, CreateBillRequestDto request);

    /**
     * Finalizes a draft bill — posts a journal entry to the GL and transitions
     * status to POSTED.
     *
     * @param tenantId     the tenant UUID
     * @param billId       the bill UUID
     * @param performedBy  the actor performing the action
     * @return the finalized Bill
     */
    Bill finalizeBill(UUID tenantId, UUID billId, String performedBy);

    /**
     * Voids a draft bill. Cannot void a finalized bill that has payments.
     */
    Bill voidBill(UUID tenantId, UUID billId, String performedBy);

    /**
     * Retrieves a bill by ID, validating tenant ownership.
     */
    Bill getBill(UUID tenantId, UUID billId);

    /**
     * Retrieves a vendor by ID, validating tenant ownership.
     */
    Vendor getVendor(UUID tenantId, UUID vendorId);

    /**
     * Lists vendors for a tenant with optional search filter.
     */
    Page<Vendor> listVendors(
            UUID tenantId,
            @Nullable String search,
            Pageable pageable);

    /**
     * Lists bills for a tenant with optional filters.
     */
    Page<BillResponseDto> listBills(
            UUID tenantId,
            @Nullable UUID vendorId,
            @Nullable BillStatus status,
            Pageable pageable);

    /**
     * Retrieves all line items for a bill.
     */
    List<BillLine> getBillLines(UUID billId);
}
