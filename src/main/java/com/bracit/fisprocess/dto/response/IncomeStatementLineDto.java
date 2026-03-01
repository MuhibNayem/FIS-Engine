package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single account line in an Income Statement section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementLineDto {

    private String accountCode;
    private String accountName;
    private long amountCents;
    private String formattedAmount;
}
