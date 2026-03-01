package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single line in a Cash Flow section.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowLineDto {

    private String label;
    private long amountCents;
}
