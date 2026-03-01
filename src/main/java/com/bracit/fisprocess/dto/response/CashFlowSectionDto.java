package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A section of the Cash Flow statement (Operating, Investing, Financing).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashFlowSectionDto {

    private String sectionName;
    private List<CashFlowLineDto> lines;
    private long sectionTotal;
}
