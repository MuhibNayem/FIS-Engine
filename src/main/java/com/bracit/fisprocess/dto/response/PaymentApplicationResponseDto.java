package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for a single payment application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentApplicationResponseDto {

    private UUID applicationId;
    private UUID invoiceId;
    private String invoiceNumber;
    private Long appliedAmount;
    private OffsetDateTime createdAt;
}
