package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Account Activity report — summarized debit/credit activity for an account
 * over a date range.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountActivityReportDto {

    private ReportMetadataDto metadata;
    private String accountCode;
    private String accountName;
    private long openingBalance;
    private long totalDebits;
    private long totalCredits;
    private long closingBalance;
    private long transactionCount;
}
