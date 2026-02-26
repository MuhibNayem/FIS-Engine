package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementRequestDto {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotNull(message = "originalJournalEntryId is required")
    private UUID originalJournalEntryId;

    @NotNull(message = "settlementDate is required")
    private LocalDate settlementDate;

    @NotNull(message = "settlementRate is required")
    @DecimalMin(value = "0.00000001", message = "settlementRate must be positive")
    private BigDecimal settlementRate;

    @NotBlank(message = "monetaryAccountCode is required")
    private String monetaryAccountCode;

    @NotBlank(message = "gainAccountCode is required")
    private String gainAccountCode;

    @NotBlank(message = "lossAccountCode is required")
    private String lossAccountCode;

    @NotBlank(message = "createdBy is required")
    private String createdBy;
}
