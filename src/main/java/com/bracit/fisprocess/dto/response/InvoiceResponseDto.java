package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for AR Invoice details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponseDto {

    private UUID invoiceId;
    private UUID tenantId;
    private UUID customerId;
    private String invoiceNumber;
    private String issueDate;
    private String dueDate;
    private String currency;
    private Long subtotalAmount;
    private Long taxAmount;
    private Long totalAmount;
    private Long paidAmount;
    private Long outstandingAmount;
    private String status;
    private String description;
    private String referenceId;
    private List<InvoiceLineResponseDto> lines;
    private OffsetDateTime createdAt;
}
