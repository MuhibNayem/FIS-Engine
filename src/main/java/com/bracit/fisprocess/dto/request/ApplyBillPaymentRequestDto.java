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
 * Request DTO for applying a bill payment to one or more bills.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyBillPaymentRequestDto {

    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    @NotEmpty(message = "At least one application is required")
    @Valid
    private List<BillPaymentApplicationRequestDto> applications;

    /**
     * Inner DTO for a single bill payment application.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillPaymentApplicationRequestDto {

        @NotNull(message = "Bill ID is required")
        private UUID billId;

        @NotNull(message = "Applied amount is required")
        private Long appliedAmount;
    }
}
