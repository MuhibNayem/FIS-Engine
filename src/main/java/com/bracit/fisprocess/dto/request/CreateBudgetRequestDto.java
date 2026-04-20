package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBudgetRequestDto {

    @NotBlank(message = "Budget name is required")
    private String name;

    @NotNull(message = "Fiscal year is required")
    @Positive(message = "Fiscal year must be positive")
    private Integer fiscalYear;

    private String status;
}