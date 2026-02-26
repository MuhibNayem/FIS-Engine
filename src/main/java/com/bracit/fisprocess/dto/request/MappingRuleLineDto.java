package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRuleLineDto {

    @NotBlank(message = "accountCodeExpression is required")
    private String accountCodeExpression;

    private boolean isCredit;

    @NotBlank(message = "amountExpression is required")
    private String amountExpression;

    @Builder.Default
    private int sortOrder = 0;
}
