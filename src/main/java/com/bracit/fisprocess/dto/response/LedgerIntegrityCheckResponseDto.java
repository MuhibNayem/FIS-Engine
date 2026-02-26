package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerIntegrityCheckResponseDto {

    private UUID tenantId;
    private long assetTotal;
    private long liabilityTotal;
    private long equityTotal;
    private long revenueTotal;
    private long expenseTotal;
    private long equationDelta;
    private boolean balanced;
}
