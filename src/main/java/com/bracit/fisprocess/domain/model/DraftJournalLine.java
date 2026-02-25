package com.bracit.fisprocess.domain.model;

import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Internal processing model representing a single Journal Line before
 * persistence.
 * Not a JPA entity.
 */
@Data
@Builder
public class DraftJournalLine {

    private String accountCode;
    private Long amountCents;
    private Long baseAmountCents;
    private boolean isCredit;
    @Nullable
    private Map<String, String> dimensions;
}
