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
    private long balanceCents;
    private String formattedBalance;
}
