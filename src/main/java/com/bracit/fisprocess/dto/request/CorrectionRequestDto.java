package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Atomic correction request: reverse original and post replacement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "reversalEventId is required")
    private String reversalEventId;

    @NotNull(message = "postedDate is required")
    private LocalDate postedDate;

    @Nullable
    private String description;

    @Nullable
    private String referenceId;

    @NotBlank(message = "transactionCurrency is required")
    private String transactionCurrency;

    @NotBlank(message = "createdBy is required")
    private String createdBy;

    @NotEmpty(message = "At least one journal line is required")
    @Valid
    private List<JournalLineRequestDto> lines;
}
