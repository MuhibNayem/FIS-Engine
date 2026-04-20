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
public class AssetCategoryResponseDto {
    private UUID id;
    private String name;
    private Integer defaultUsefulLifeMonths;
    private String depreciationMethod;
    private String glAccountCode;
    private String tenantId;
}