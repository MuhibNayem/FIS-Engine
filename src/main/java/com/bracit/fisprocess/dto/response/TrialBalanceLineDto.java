package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single line in a Trial Balance report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceLineDto {

    private String accountCode;
    private String accountName;
    private String accountType;
    private String parentAccountCode;
    private int hierarchyLevel;
    private boolean leaf;
    private long debitBalance;
    private long creditBalance;
    private long rolledUpDebitBalance;
    private long rolledUpCreditBalance;
}
