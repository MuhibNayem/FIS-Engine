package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.Invoice;
import com.bracit.fisprocess.domain.entity.InvoiceLine;
import com.bracit.fisprocess.domain.enums.InvoiceStatus;
import com.bracit.fisprocess.dto.request.CreateInvoiceRequestDto;
import com.bracit.fisprocess.dto.request.FinalizeInvoiceRequestDto;
import com.bracit.fisprocess.dto.response.InvoiceResponseDto;
import com.bracit.fisprocess.service.InvoiceService;
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
 * REST controller for AR Invoice management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ar/invoices")
@RequiredArgsConstructor
@ApiVersion(1)
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final ModelMapper modelMapper;

    /**
     * Creates a new draft Invoice.
     *
     * @param tenantId the tenant UUID
     * @param request  the invoice creation details
     * @return 201 Created with the new invoice details
     */
    @PostMapping
    public ResponseEntity<InvoiceResponseDto> createInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateInvoiceRequestDto request) {
        Invoice invoice = invoiceService.createInvoice(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(invoice));
    }

    /**
     * Finalizes a draft invoice — posts a journal entry to the GL.
     *
     * @param tenantId  the tenant UUID
     * @param invoiceId the invoice UUID
     * @param request   empty body (action trigger)
     * @return 200 OK with the finalized invoice details
     */
    @PostMapping("/{invoiceId}/finalize")
    public ResponseEntity<InvoiceResponseDto> finalizeInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @RequestBody(required = false) @Nullable FinalizeInvoiceRequestDto request) {
        Invoice invoice = invoiceService.finalizeInvoice(tenantId, invoiceId, performedBy);
        return ResponseEntity.ok(toResponseDto(invoice));
    }

    /**
     * Voids a draft invoice.
     *
     * @param tenantId  the tenant UUID
     * @param invoiceId the invoice UUID
     * @return 200 OK with the voided invoice details
     */
    @PostMapping("/{invoiceId}/void")
    public ResponseEntity<InvoiceResponseDto> voidInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        Invoice invoice = invoiceService.voidInvoice(tenantId, invoiceId, performedBy);
        return ResponseEntity.ok(toResponseDto(invoice));
    }

    /**
     * Retrieves an invoice by ID.
     *
     * @param tenantId  the tenant UUID
     * @param invoiceId the invoice UUID
     * @return 200 OK with invoice details
     */
    @GetMapping("/{invoiceId}")
    public ResponseEntity<InvoiceResponseDto> getInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID invoiceId) {
        Invoice invoice = invoiceService.getInvoice(tenantId, invoiceId);
        return ResponseEntity.ok(toResponseDto(invoice));
    }

    /**
     * Lists invoices for a tenant with optional filters.
     *
     * @param tenantId   the tenant UUID
     * @param customerId optional customer filter
     * @param status     optional status filter
     * @param pageable   pagination parameters
     * @return 200 OK with paginated invoice list
     */
    @GetMapping
    public ResponseEntity<Page<InvoiceResponseDto>> listInvoices(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID customerId,
            @RequestParam(required = false) @Nullable InvoiceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<InvoiceResponseDto> response = invoiceService.listInvoices(tenantId, customerId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all line items for an invoice.
     *
     * @param invoiceId the invoice UUID
     * @return 200 OK with invoice line items
     */
    @GetMapping("/{invoiceId}/lines")
    public ResponseEntity<List<com.bracit.fisprocess.dto.response.InvoiceLineResponseDto>> getInvoiceLines(
            @PathVariable UUID invoiceId) {
        List<InvoiceLine> lines = invoiceService.getInvoiceLines(invoiceId);
        List<com.bracit.fisprocess.dto.response.InvoiceLineResponseDto> dtos = lines.stream()
                .map(this::toLineResponseDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    private InvoiceResponseDto toResponseDto(Invoice invoice) {
        InvoiceResponseDto dto = modelMapper.map(invoice, InvoiceResponseDto.class);
        dto.setInvoiceId(invoice.getInvoiceId());
        dto.setOutstandingAmount(invoice.getOutstandingAmount());
        dto.setIssueDate(invoice.getIssueDate().toString());
        dto.setDueDate(invoice.getDueDate().toString());
        dto.setStatus(invoice.getStatus().name());

        if (invoice.getLines() != null && !invoice.getLines().isEmpty()) {
            dto.setLines(invoice.getLines().stream()
                    .map(this::toLineResponseDto)
                    .toList());
        }

        return dto;
    }

    private com.bracit.fisprocess.dto.response.InvoiceLineResponseDto toLineResponseDto(InvoiceLine line) {
        com.bracit.fisprocess.dto.response.InvoiceLineResponseDto dto =
                modelMapper.map(line, com.bracit.fisprocess.dto.response.InvoiceLineResponseDto.class);
        dto.setInvoiceLineId(line.getInvoiceLineId());
        return dto;
    }
}
