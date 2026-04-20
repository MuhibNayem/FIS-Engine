package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for AR Payment details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {

    private UUID paymentId;
    private UUID tenantId;
    private UUID customerId;
    private Long amount;
    private String paymentDate;
    private String method;
    private String reference;
    private String status;
    private List<PaymentApplicationResponseDto> applications;
    private OffsetDateTime createdAt;
}
