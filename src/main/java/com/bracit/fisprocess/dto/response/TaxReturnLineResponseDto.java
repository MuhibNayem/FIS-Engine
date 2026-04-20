package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for a Tax Return Line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturnLineResponseDto {

    private UUID taxReturnLineId;
    private UUID taxRateId;
    private String taxRateCode;
    private Long taxableAmount;
    private Long taxAmount;
    private String direction;
}
