package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRuleLineResponseDto {

    private String accountCodeExpression;
    private boolean isCredit;
    private String amountExpression;
    private int sortOrder;
}
