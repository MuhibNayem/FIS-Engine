package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single transaction entry in a General Ledger detail report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralLedgerEntryDto {

    private UUID journalEntryId;
    private Long sequenceNumber;
    private LocalDate postedDate;
    private String description;
    private long debitAmount;
    private long creditAmount;
    private long runningBalance;
}
