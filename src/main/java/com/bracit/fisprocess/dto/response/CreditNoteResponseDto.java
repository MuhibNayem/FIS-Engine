package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for AR Credit Note details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditNoteResponseDto {

    private UUID creditNoteId;
    private UUID tenantId;
    private UUID customerId;
    private UUID originalInvoiceId;
    private Long amount;
    private String reason;
    private String status;
    private OffsetDateTime createdAt;
}
