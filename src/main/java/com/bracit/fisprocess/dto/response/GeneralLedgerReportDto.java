package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * General Ledger Detail report — all transactions for a specific account
 * with running balance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralLedgerReportDto {

    private ReportMetadataDto metadata;
    private String accountCode;
    private String accountName;
    private long openingBalance;
    private List<GeneralLedgerEntryDto> entries;
    private long closingBalance;
}
