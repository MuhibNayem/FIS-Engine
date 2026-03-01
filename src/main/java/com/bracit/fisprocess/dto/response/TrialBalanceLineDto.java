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
    private long debitBalance;
    private long creditBalance;
}
