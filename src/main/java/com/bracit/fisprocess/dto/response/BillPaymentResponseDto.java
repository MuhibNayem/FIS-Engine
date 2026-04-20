package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for AP Bill Payment details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentResponseDto {

    private UUID billPaymentId;
    private UUID tenantId;
    private UUID vendorId;
    private Long amount;
    private String paymentDate;
    private String method;
    private String reference;
    private String status;
    private List<BillPaymentApplicationResponseDto> applications;
    private OffsetDateTime createdAt;
}
