package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for AP Debit Note details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitNoteResponseDto {

    private UUID debitNoteId;
    private UUID tenantId;
    private UUID vendorId;
    private UUID originalBillId;
    private Long amount;
    private String reason;
    private String status;
    private OffsetDateTime createdAt;
}
