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
public class FixedAssetResponseDto {
    private String id;
    private String tenantId;
    private UUID categoryId;
    private String assetTag;
    private String name;
    private LocalDate acquisitionDate;
    private Long acquisitionCost;
    private Long salvageValue;
    private Integer usefulLifeMonths;
    private String depreciationMethod;
    private Long accumulatedDepreciation;
    private Long netBookValue;
    private String location;
    private String status;
    private LocalDate disposalDate;
    private Long disposalProceeds;
}