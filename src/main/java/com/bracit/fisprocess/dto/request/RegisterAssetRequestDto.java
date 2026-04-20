package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class RegisterAssetRequestDto {

    @NotNull(message = "Category ID is required")
    private UUID categoryId;

    @NotBlank(message = "Asset tag is required")
    private String assetTag;

    @NotBlank(message = "Asset name is required")
    private String name;

    @NotNull(message = "Acquisition date is required")
    private LocalDate acquisitionDate;

    @NotNull(message = "Acquisition cost is required")
    @Positive(message = "Acquisition cost must be positive")
    private Long acquisitionCost;

    private Long salvageValue;

    @NotNull(message = "Useful life months is required")
    @Positive(message = "Useful life must be positive")
    private Integer usefulLifeMonths;

    private String depreciationMethod;

    private String location;
}