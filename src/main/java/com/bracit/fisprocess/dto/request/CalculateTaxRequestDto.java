package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.TaxDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for calculating tax on an amount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateTaxRequestDto {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Long amount;

    @NotNull(message = "Tax group ID is required")
    private UUID taxGroupId;

    @Builder.Default
    private Boolean isInclusive = false;

    @NotNull(message = "Tax direction is required")
    private TaxDirection direction;
}
