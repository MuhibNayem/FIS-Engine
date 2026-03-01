package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A section in a Balance Sheet (e.g., Assets, Liabilities, Equity).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetSectionDto {

    private String sectionName;
    private List<BalanceSheetLineDto> lines;
    private long sectionTotal;
}
