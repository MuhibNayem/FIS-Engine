package com.bracit.fisprocess.domain.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Internal processing model representing a Journal Entry before persistence.
 * <p>
 * Used as the intermediate representation between the controller/mapping engine
 * and the persistence layer. Not a JPA entity.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DraftJournalEntry {

    private UUID tenantId;
    private String eventId;
    private LocalDate postedDate;
    private LocalDate effectiveDate;
    private LocalDate transactionDate;
    @Nullable
    private String description;
    @Nullable
    private String referenceId;
    private String transactionCurrency;
    private String baseCurrency;
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;
    private String createdBy;
    @Nullable
    private UUID reversalOfId;
    private List<DraftJournalLine> lines;
    @Builder.Default
    private boolean autoReverse = false;
}
