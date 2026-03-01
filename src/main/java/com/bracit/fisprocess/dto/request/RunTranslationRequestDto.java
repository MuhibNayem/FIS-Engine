package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for functional-currency translation run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunTranslationRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "createdBy is required")
    private String createdBy;

    @NotBlank(message = "ctaOciAccountCode is required")
    private String ctaOciAccountCode;

    @NotBlank(message = "translationReserveAccountCode is required")
    private String translationReserveAccountCode;
}
