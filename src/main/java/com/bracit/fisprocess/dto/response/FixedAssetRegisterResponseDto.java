package com.bracit.fisprocess.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedAssetRegisterResponseDto {
    private UUID categoryId;
    private String categoryName;
    private String status;
    private int totalAssets;
    private long totalAcquisitionCost;
    private long totalAccumulatedDepreciation;
    private long totalNetBookValue;
    private List<AssetDetailDto> assets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetDetailDto {
        private UUID assetId;
        private String assetTag;
        private String name;
        private String location;
        private String depreciationMethod;
        private LocalDate acquisitionDate;
        private long acquisitionCost;
        private long salvageValue;
        private int usefulLifeMonths;
        private long accumulatedDepreciation;
        private long netBookValue;
        private String status;
    }
}