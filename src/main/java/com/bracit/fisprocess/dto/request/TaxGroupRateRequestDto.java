package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for a Tax Group Rate entry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxGroupRateRequestDto {

    @NotNull(message = "Tax rate ID is required")
    private UUID taxRateId;

    @Builder.Default
    private Boolean isCompound = false;
}
