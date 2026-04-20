package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a Bill Payment Application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentApplicationResponseDto {

    private UUID applicationId;
    private UUID paymentId;
    private UUID billId;
    private String billNumber;
    private Long appliedAmount;
    private OffsetDateTime createdAt;
}
