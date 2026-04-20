package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.BillPayment;
import com.bracit.fisprocess.domain.entity.BillPaymentApplication;
import com.bracit.fisprocess.dto.request.ApplyBillPaymentRequestDto;
import com.bracit.fisprocess.dto.request.RecordBillPaymentRequestDto;
import com.bracit.fisprocess.dto.response.BillPaymentResponseDto;
import com.bracit.fisprocess.service.BillPaymentService;
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
import java.util.stream.Collectors;

/**
 * REST controller for AP Bill Payment management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ap/payments")
@RequiredArgsConstructor
@ApiVersion(1)
public class BillPaymentController {

    private final BillPaymentService billPaymentService;
    private final ModelMapper modelMapper;

    /**
     * Records a new payment to a vendor.
     *
     * @param tenantId the tenant UUID
     * @param request  the payment details
     * @return 201 Created with the new payment details
     */
    @PostMapping
    public ResponseEntity<BillPaymentResponseDto> recordPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody RecordBillPaymentRequestDto request) {
        BillPayment payment = billPaymentService.recordPayment(tenantId, request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(payment));
    }

    /**
     * Applies a payment to one or more bills — posts journal entries to the GL.
     *
     * @param tenantId  the tenant UUID
     * @param paymentId the payment UUID
     * @param request   the application details
     * @return 200 OK with the updated payment details
     */
    @PostMapping("/{paymentId}/apply")
    public ResponseEntity<BillPaymentResponseDto> applyPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody ApplyBillPaymentRequestDto request) {
        BillPayment payment = billPaymentService.applyPayment(tenantId, request, performedBy);
        return ResponseEntity.ok(toResponseDto(payment));
    }

    /**
     * Retrieves a payment by ID.
     *
     * @param tenantId  the tenant UUID
     * @param paymentId the payment UUID
     * @return 200 OK with payment details
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<BillPaymentResponseDto> getPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID paymentId) {
        BillPayment payment = billPaymentService.getPayment(tenantId, paymentId);
        return ResponseEntity.ok(toResponseDto(payment));
    }

    /**
     * Lists payments for a tenant with optional vendor filter.
     *
     * @param tenantId the tenant UUID
     * @param vendorId optional vendor filter
     * @param pageable pagination parameters
     * @return 200 OK with paginated payment list
     */
    @GetMapping
    public ResponseEntity<Page<BillPaymentResponseDto>> listPayments(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID vendorId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<BillPaymentResponseDto> response = billPaymentService.listPayments(tenantId, vendorId, pageable);
        return ResponseEntity.ok(response);
    }

    private BillPaymentResponseDto toResponseDto(BillPayment payment) {
        BillPaymentResponseDto dto = modelMapper.map(payment, BillPaymentResponseDto.class);
        dto.setBillPaymentId(payment.getBillPaymentId());
        dto.setPaymentDate(payment.getPaymentDate().toString());
        dto.setMethod(payment.getMethod().name());
        dto.setStatus(payment.getStatus().name());

        if (payment.getApplications() != null && !payment.getApplications().isEmpty()) {
            dto.setApplications(payment.getApplications().stream()
                    .map(app -> {
                        com.bracit.fisprocess.dto.response.BillPaymentApplicationResponseDto appDto =
                                modelMapper.map(app, com.bracit.fisprocess.dto.response.BillPaymentApplicationResponseDto.class);
                        appDto.setApplicationId(app.getApplicationId());
                        return appDto;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
