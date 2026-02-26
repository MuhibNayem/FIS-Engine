package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for full reversal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "reason is required")
    private String reason;

    @NotBlank(message = "createdBy is required")
    private String createdBy;
}
