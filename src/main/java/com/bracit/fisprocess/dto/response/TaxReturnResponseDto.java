package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Tax Return details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxReturnResponseDto {

    private UUID taxReturnId;
    private UUID tenantId;
    private UUID jurisdictionId;
    private String period;
    private OffsetDateTime filedAt;
    private Long totalOutputTax;
    private Long totalInputTax;
    private Long netPayable;
    private String status;
    private List<TaxReturnLineResponseDto> lines;
    private OffsetDateTime createdAt;
}
