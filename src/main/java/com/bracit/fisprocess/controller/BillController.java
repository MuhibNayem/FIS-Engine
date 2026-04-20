package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.Bill;
import com.bracit.fisprocess.domain.entity.BillLine;
import com.bracit.fisprocess.domain.enums.BillStatus;
import com.bracit.fisprocess.dto.request.CreateBillRequestDto;
import com.bracit.fisprocess.dto.request.FinalizeBillRequestDto;
import com.bracit.fisprocess.dto.response.BillLineResponseDto;
import com.bracit.fisprocess.dto.response.BillResponseDto;
import com.bracit.fisprocess.service.BillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for AP Bill management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ap/bills")
@RequiredArgsConstructor
@ApiVersion(1)
public class BillController {

    private final BillService billService;
    private final ModelMapper modelMapper;

    /**
     * Creates a new draft Bill.
     *
     * @param tenantId the tenant UUID
     * @param request  the bill creation details
     * @return 201 Created with the new bill details
     */
    @PostMapping
    public ResponseEntity<BillResponseDto> createBill(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateBillRequestDto request) {
        Bill bill = billService.createBill(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(bill));
    }

    /**
     * Finalizes a draft bill — posts a journal entry to the GL.
     *
     * @param tenantId the tenant UUID
     * @param billId   the bill UUID
     * @param request  empty body (action trigger)
     * @return 200 OK with the finalized bill details
     */
    @PostMapping("/{billId}/finalize")
    public ResponseEntity<BillResponseDto> finalizeBill(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID billId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @RequestBody(required = false) @Nullable FinalizeBillRequestDto request) {
        Bill bill = billService.finalizeBill(tenantId, billId, performedBy);
        return ResponseEntity.ok(toResponseDto(bill));
    }

    /**
     * Voids a draft bill.
     *
     * @param tenantId the tenant UUID
     * @param billId   the bill UUID
     * @return 200 OK with the voided bill details
     */
    @PostMapping("/{billId}/void")
    public ResponseEntity<BillResponseDto> voidBill(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID billId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        Bill bill = billService.voidBill(tenantId, billId, performedBy);
        return ResponseEntity.ok(toResponseDto(bill));
    }

    /**
     * Retrieves a bill by ID.
     *
     * @param tenantId the tenant UUID
     * @param billId   the bill UUID
     * @return 200 OK with bill details
     */
    @GetMapping("/{billId}")
    public ResponseEntity<BillResponseDto> getBill(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID billId) {
        Bill bill = billService.getBill(tenantId, billId);
        return ResponseEntity.ok(toResponseDto(bill));
    }

    /**
     * Lists bills for a tenant with optional filters.
     *
     * @param tenantId the tenant UUID
     * @param vendorId optional vendor filter
     * @param status   optional status filter
     * @param pageable pagination parameters
     * @return 200 OK with paginated bill list
     */
    @GetMapping
    public ResponseEntity<Page<BillResponseDto>> listBills(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID vendorId,
            @RequestParam(required = false) @Nullable BillStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<BillResponseDto> response = billService.listBills(tenantId, vendorId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all line items for a bill.
     *
     * @param billId the bill UUID
     * @return 200 OK with bill line items
     */
    @GetMapping("/{billId}/lines")
    public ResponseEntity<List<BillLineResponseDto>> getBillLines(
            @PathVariable UUID billId) {
        List<BillLine> lines = billService.getBillLines(billId);
        List<BillLineResponseDto> dtos = lines.stream()
                .map(this::toLineResponseDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private BillResponseDto toResponseDto(Bill bill) {
        BillResponseDto dto = modelMapper.map(bill, BillResponseDto.class);
        dto.setBillId(bill.getBillId());
        dto.setOutstandingAmount(bill.getOutstandingAmount());
        dto.setBillDate(bill.getBillDate().toString());
        dto.setDueDate(bill.getDueDate().toString());
        dto.setStatus(bill.getStatus().name());

        if (bill.getLines() != null && !bill.getLines().isEmpty()) {
            dto.setLines(bill.getLines().stream()
                    .map(this::toLineResponseDto)
                    .toList());
        }

        return dto;
    }

    private BillLineResponseDto toLineResponseDto(BillLine line) {
        BillLineResponseDto dto = modelMapper.map(line, BillLineResponseDto.class);
        dto.setBillLineId(line.getBillLineId());
        return dto;
    }
}
