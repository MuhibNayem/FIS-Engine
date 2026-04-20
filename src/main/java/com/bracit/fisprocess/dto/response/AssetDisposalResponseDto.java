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
public class AssetDisposalResponseDto {
    private UUID id;
    private String tenantId;
    private UUID assetId;
    private LocalDate disposalDate;
    private Long saleProceeds;
    private Long netBookValue;
    private Long gainLoss;
    private String disposalType;
    private String createdBy;
}