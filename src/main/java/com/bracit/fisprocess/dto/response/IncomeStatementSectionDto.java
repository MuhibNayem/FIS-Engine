package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A section in an Income Statement (Revenue or Expenses).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementSectionDto {

    private String sectionName;
    private List<IncomeStatementLineDto> lines;
    private long sectionTotal;
}
