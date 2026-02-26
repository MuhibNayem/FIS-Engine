package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for period-end revaluation run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRevaluationRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "createdBy is required")
    private String createdBy;

    @NotBlank(message = "reserveAccountCode is required")
    private String reserveAccountCode;

    @NotBlank(message = "gainAccountCode is required")
    private String gainAccountCode;

    @NotBlank(message = "lossAccountCode is required")
    private String lossAccountCode;
}
