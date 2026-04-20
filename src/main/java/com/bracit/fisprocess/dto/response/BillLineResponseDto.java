package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for a single AP Bill line item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillLineResponseDto {

    private UUID billLineId;
    private String description;
    private Long quantity;
    private Long unitPrice;
    private Long taxRate;
    private Long lineTotal;
    private UUID glAccountId;
    private Integer sortOrder;
}
