package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single account line in a Balance Sheet section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetLineDto {

    private String accountCode;
    private String accountName;
    private String parentAccountCode;
    private int hierarchyLevel;
    private boolean leaf;
    private long balanceCents;
    private long rolledUpBalanceCents;
    private String formattedBalance;
    private String formattedRolledUpBalance;
}
