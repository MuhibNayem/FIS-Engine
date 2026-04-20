package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.TaxType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating a new Tax Rate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaxRateRequestDto {

    @NotBlank(message = "Tax rate code is required")
    @Size(max = 50, message = "Tax rate code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Tax rate name is required")
    @Size(max = 255, message = "Tax rate name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Tax rate is required")
    @PositiveOrZero(message = "Tax rate must be zero or positive")
    private BigDecimal rate;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    @Nullable
    private LocalDate effectiveTo;

    @NotNull(message = "Tax type is required")
    private TaxType type;
}
