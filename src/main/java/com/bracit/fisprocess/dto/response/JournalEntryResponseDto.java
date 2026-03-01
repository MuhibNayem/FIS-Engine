package com.bracit.fisprocess.dto.response;

import com.bracit.fisprocess.domain.enums.JournalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for Journal Entry details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponseDto {

    private UUID journalEntryId;
    private LocalDate postedDate;
    private LocalDate effectiveDate;
    private LocalDate transactionDate;
    private JournalStatus status;
    @Nullable
    private String description;
    @Nullable
    private String referenceId;
    private String transactionCurrency;
    private String baseCurrency;
    private BigDecimal exchangeRate;
    private int lineCount;
    @Nullable
    private UUID reversalOfId;
    private String createdBy;
    private OffsetDateTime createdAt;
    @Nullable
    private Integer fiscalYear;
    @Nullable
    private Long sequenceNumber;
}
