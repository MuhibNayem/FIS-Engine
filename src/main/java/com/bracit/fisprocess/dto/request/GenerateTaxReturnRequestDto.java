package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Request DTO for generating a Tax Return.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateTaxReturnRequestDto {

    @NotNull(message = "Jurisdiction ID is required")
    private UUID jurisdictionId;

    @NotNull(message = "Period is required")
    private YearMonth period;
}
