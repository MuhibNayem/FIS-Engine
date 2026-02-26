package com.bracit.fisprocess.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Financial event ingestion payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialEventRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "eventType is required")
    private String eventType;

    @NotNull(message = "occurredAt is required")
    private OffsetDateTime occurredAt;

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

    @Nullable
    @Valid
    private List<JournalLineRequestDto> lines;

    @Nullable
    private Map<String, Object> payload;
}
