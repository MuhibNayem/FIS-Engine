package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for Tax Rate details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxRateResponseDto {

    private UUID taxRateId;
    private UUID tenantId;
    private String code;
    private String name;
    private BigDecimal rate;
    private String effectiveFrom;
    private String effectiveTo;
    private String type;
    private Boolean isActive;
    private OffsetDateTime createdAt;
}
