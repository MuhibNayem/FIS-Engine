package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetVarianceResponseDto {
    private UUID budgetId;
    private String budgetName;
    private Integer fiscalYear;

    @Builder.Default
    private List<BudgetVarianceLineDto> lines = new ArrayList<>();

    private Long totalBudgeted;
    private Long totalActual;
    private Long totalVariance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetVarianceLineDto {
        private String accountCode;
        private String department;
        private String month;
        private Long budgetedAmount;
        private Long actualAmount;
        private Long variance;
        private Double variancePercent;
    }
}