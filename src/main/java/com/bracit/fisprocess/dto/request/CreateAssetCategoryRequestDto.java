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
public class CreateAssetCategoryRequestDto {

    @NotBlank(message = "Category name is required")
    private String name;

    @NotNull(message = "Default useful life months is required")
    @Positive(message = "Default useful life must be positive")
    private Integer defaultUsefulLifeMonths;

    private String depreciationMethod;

    private String glAccountCode;
}