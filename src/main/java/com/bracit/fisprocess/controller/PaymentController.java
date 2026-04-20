package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.ARPayment;
import com.bracit.fisprocess.domain.entity.PaymentApplication;
import com.bracit.fisprocess.dto.request.ApplyPaymentRequestDto;
import com.bracit.fisprocess.dto.request.RecordPaymentRequestDto;
import com.bracit.fisprocess.dto.response.PaymentResponseDto;
import com.bracit.fisprocess.service.PaymentService;
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
 * REST controller for AR Payment management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ar/payments")
@RequiredArgsConstructor
@ApiVersion(1)
public class PaymentController {

    private final PaymentService paymentService;
    private final ModelMapper modelMapper;

    /**
     * Records a new payment from a customer.
     *
     * @param tenantId the tenant UUID
     * @param request  the payment details
     * @return 201 Created with the new payment details
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDto> recordPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody RecordPaymentRequestDto request) {
        ARPayment payment = paymentService.recordPayment(tenantId, request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(payment));
    }

    /**
     * Applies a payment to one or more invoices — posts journal entries to the GL.
     *
     * @param tenantId  the tenant UUID
     * @param paymentId the payment UUID
     * @param request   the application details
     * @return 200 OK with the updated payment details
     */
    @PostMapping("/{paymentId}/apply")
    public ResponseEntity<PaymentResponseDto> applyPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody ApplyPaymentRequestDto request) {
        ARPayment payment = paymentService.applyPayment(tenantId, request, performedBy);
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
    public ResponseEntity<PaymentResponseDto> getPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID paymentId) {
        ARPayment payment = paymentService.getPayment(tenantId, paymentId);
        return ResponseEntity.ok(toResponseDto(payment));
    }

    /**
     * Lists payments for a tenant with optional customer filter.
     *
     * @param tenantId   the tenant UUID
     * @param customerId optional customer filter
     * @param pageable   pagination parameters
     * @return 200 OK with paginated payment list
     */
    @GetMapping
    public ResponseEntity<Page<PaymentResponseDto>> listPayments(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponseDto> response = paymentService.listPayments(tenantId, customerId, pageable);
        return ResponseEntity.ok(response);
    }

    private PaymentResponseDto toResponseDto(ARPayment payment) {
        PaymentResponseDto dto = modelMapper.map(payment, PaymentResponseDto.class);
        dto.setPaymentId(payment.getPaymentId());
        dto.setPaymentDate(payment.getPaymentDate().toString());
        dto.setMethod(payment.getMethod().name());
        dto.setStatus(payment.getStatus().name());

        if (payment.getApplications() != null && !payment.getApplications().isEmpty()) {
            dto.setApplications(payment.getApplications().stream()
                    .map(app -> {
                        com.bracit.fisprocess.dto.response.PaymentApplicationResponseDto appDto =
                                modelMapper.map(app, com.bracit.fisprocess.dto.response.PaymentApplicationResponseDto.class);
                        appDto.setApplicationId(app.getApplicationId());
                        return appDto;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}
