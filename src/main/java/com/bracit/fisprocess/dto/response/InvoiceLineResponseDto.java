package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Response DTO for a single invoice line item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLineResponseDto {

    private UUID invoiceLineId;
    private String description;
    private Long quantity;
    private Long unitPrice;
    private Long taxRate;
    private Long lineTotal;

    @Nullable
    private UUID glAccountId;

    private Integer sortOrder;
}
