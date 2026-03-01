package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for performing a fiscal year-end close.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearEndCloseRequestDto {

    @NotNull(message = "fiscalYear is required")
    private Integer fiscalYear;

    @NotBlank(message = "retainedEarningsAccountCode is required")
    private String retainedEarningsAccountCode;

    @NotBlank(message = "createdBy is required")
    private String createdBy;
}
