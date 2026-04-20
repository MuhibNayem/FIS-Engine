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
public class AssetDepreciationRunResponseDto {
    private UUID id;
    private String tenantId;
    private String period;
    private LocalDate runDate;
    private Long totalDepreciation;
    private String status;
    private String createdBy;
}