package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Request DTO for creating a new Tax Group.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaxGroupRequestDto {

    @NotBlank(message = "Tax group name is required")
    @Size(max = 100, message = "Tax group name must not exceed 100 characters")
    private String name;

    @Nullable
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotEmpty(message = "At least one tax group rate is required")
    @Valid
    private List<TaxGroupRateRequestDto> rates;
}
