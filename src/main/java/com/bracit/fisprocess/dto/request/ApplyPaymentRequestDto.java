package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for applying a payment to one or more invoices.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPaymentRequestDto {

    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    @NotEmpty(message = "At least one application is required")
    @Valid
    private List<PaymentApplicationRequestDto> applications;

    /**
     * Inner DTO for a single payment application.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentApplicationRequestDto {

        @NotNull(message = "Invoice ID is required")
        private UUID invoiceId;

        @NotNull(message = "Applied amount is required")
        private Long appliedAmount;
    }
}
