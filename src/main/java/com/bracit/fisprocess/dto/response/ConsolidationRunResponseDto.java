package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationRunResponseDto {
    private UUID id;
    private UUID tenantId;
    private UUID groupId;
    private String period;
    private LocalDate runDate;
    private String status;
    private Long totalAssets;
    private Long totalLiabilities;
    private Long totalEquity;
    private Long netIncome;
}