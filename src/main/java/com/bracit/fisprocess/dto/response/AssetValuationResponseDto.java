package com.bracit.fisprocess.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetValuationResponseDto {
    private LocalDate valuationDate;
    private int totalAssets;
    private long totalAcquisitionCost;
    private long totalAccumulatedDepreciation;
    private long totalNetBookValue;
    private List<ValuationDetailDto> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValuationDetailDto {
        private UUID assetId;
        private String assetTag;
        private String name;
        private String categoryName;
        private String depreciationMethod;
        private LocalDate acquisitionDate;
        private int usefulLifeMonths;
        private int ageInMonths;
        private int remainingLifeMonths;
        private long acquisitionCost;
        private long salvageValue;
        private long depreciableAmount;
        private long monthlyDepreciation;
        private long accumulatedDepreciation;
        private long netBookValue;
        private double depreciationPercentage;
    }
}