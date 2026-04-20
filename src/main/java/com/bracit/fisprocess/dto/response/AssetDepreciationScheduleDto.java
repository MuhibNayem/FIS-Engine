package com.bracit.fisprocess.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDepreciationScheduleDto {
    private UUID assetId;
    private String assetTag;
    private String assetName;
    private String depreciationMethod;
    private long acquisitionCost;
    private long salvageValue;
    private int usefulLifeMonths;
    private int remainingLifeMonths;
    private long monthlyDepreciation;
    private long accumulatedDepreciation;
    private long netBookValue;
    private LocalDate nextDepreciationDate;
    private LocalDate endOfLifeDate;
}