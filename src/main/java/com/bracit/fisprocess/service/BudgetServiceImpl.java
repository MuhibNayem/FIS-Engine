package com.bracit.fisprocess.service.impl;
import com.bracit.fisprocess.domain.entity.Budget;
import com.bracit.fisprocess.domain.entity.BudgetLine;
import com.bracit.fisprocess.domain.enums.BudgetStatus;
import com.bracit.fisprocess.dto.response.BudgetVarianceResponseDto;
import com.bracit.fisprocess.dto.response.BudgetVarianceResponseDto.BudgetVarianceLineDto;
import com.bracit.fisprocess.exception.BudgetNotFoundException;
import com.bracit.fisprocess.exception.BudgetThresholdExceededException;
import com.bracit.fisprocess.repository.BudgetLineRepository;
import com.bracit.fisprocess.repository.BudgetRepository;
import com.bracit.fisprocess.repository.ReportingRepository;
import com.bracit.fisprocess.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepo;
    private final BudgetLineRepository lineRepo;
    private final ReportingRepository reportingRepo;
    private final ModelMapper mapper;

    @Override
    @Transactional
    public com.bracit.fisprocess.dto.response.BudgetResponseDto create(UUID tenantId,
            com.bracit.fisprocess.dto.request.CreateBudgetRequestDto req, String performedBy) {
        var budget = Budget.builder()
            .tenantId(tenantId).name(req.getName()).fiscalYear(req.getFiscalYear())
            .createdBy(performedBy).status(BudgetStatus.DRAFT).build();
        var saved = budgetRepo.save(budget);
        return mapper.map(saved, com.bracit.fisprocess.dto.response.BudgetResponseDto.class);
    }

    @Override
    @Transactional
    public com.bracit.fisprocess.dto.response.BudgetResponseDto approve(UUID tenantId, UUID id) {
        var budget = budgetRepo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new BudgetNotFoundException(id));
        budget.setStatus(BudgetStatus.APPROVED);
        return mapper.map(budgetRepo.save(budget), com.bracit.fisprocess.dto.response.BudgetResponseDto.class);
    }

    @Override
    public BudgetVarianceResponseDto getVariance(UUID tenantId, UUID budgetId) {
        Budget budget = budgetRepo.findByTenantIdAndId(tenantId, budgetId)
            .orElseThrow(() -> new BudgetNotFoundException(budgetId));

        List<BudgetLine> lines = lineRepo.findByBudgetId(budgetId);
        List<BudgetVarianceLineDto> varianceLines = new ArrayList<>();

        long totalBudgeted = 0;
        long totalActual = 0;

        for (BudgetLine line : lines) {
            // Parse month "2026-01" to date range
            YearMonth ym = YearMonth.parse(line.getMonth());
            LocalDate fromDate = ym.atDay(1);
            LocalDate toDate = ym.atEndOfMonth();

            // Query GL actuals for this account code and period
            long actualAmount = queryActualAmount(tenantId, line.getAccountCode(), fromDate, toDate);

            long budgetedAmount = line.getBudgetedAmount() != null ? line.getBudgetedAmount() : 0;
            long variance = actualAmount - budgetedAmount; // Positive = over budget, Negative = under budget

            BigDecimal variancePct = budgetedAmount > 0
                ? BigDecimal.valueOf(variance).divide(BigDecimal.valueOf(budgetedAmount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

            varianceLines.add(BudgetVarianceLineDto.builder()
                .accountCode(line.getAccountCode())
                .department(line.getDepartment())
                .month(line.getMonth())
                .budgetedAmount(budgetedAmount)
                .actualAmount(actualAmount)
                .variance(variance)
                .variancePercent(variancePct.multiply(BigDecimal.valueOf(100)).doubleValue())
                .build());

            totalBudgeted += budgetedAmount;
            totalActual += actualAmount;
        }

        return BudgetVarianceResponseDto.builder()
            .budgetId(budgetId)
            .budgetName(budget.getName())
            .fiscalYear(budget.getFiscalYear())
            .lines(varianceLines)
            .totalBudgeted(totalBudgeted)
            .totalActual(totalActual)
            .totalVariance(totalActual - totalBudgeted)
            .build();
    }

    private long queryActualAmount(UUID tenantId, String accountCode, LocalDate fromDate, LocalDate toDate) {
        List<Map<String, Object>> results = reportingRepo.findNetMovementByAccountType(
            tenantId, fromDate, toDate);

        for (Map<String, Object> row : results) {
            String code = (String) row.get("account_code");
            if (accountCode.equals(code)) {
                Object netMovement = row.get("net_movement");
                if (netMovement instanceof Number) {
                    return Math.abs(((Number) netMovement).longValue()); // Use absolute value for comparison
                }
            }
        }
        return 0L;
    }

    @Override
    @Transactional
    public void validateBudgetThreshold(UUID tenantId, String accountCode, long amount, LocalDate date) {
        String month = String.format("%04d-%02d", date.getYear(), date.getMonthValue());
        Integer fiscalYear = date.getYear();

        // Find approved budgets for this fiscal year
        List<Budget> activeBudgets = budgetRepo.findByTenantIdAndFiscalYearAndStatus(
            tenantId, fiscalYear, BudgetStatus.APPROVED);

        for (Budget budget : activeBudgets) {
            List<BudgetLine> lines = lineRepo.findByBudgetIdAndAccountCode(budget.getId(), accountCode);
            for (BudgetLine line : lines) {
                if (month.equals(line.getMonth())) {
                    long budgeted = line.getBudgetedAmount() != null ? line.getBudgetedAmount() : 0;

                    // Get YTD actual including this transaction
                    YearMonth ym = YearMonth.parse(month);
                    LocalDate fromDate = ym.atDay(1);
                    LocalDate toDate = ym.atEndOfMonth();
                    long ytdActual = queryActualAmount(tenantId, accountCode, fromDate, toDate);
                    long projected = ytdActual + amount;

                    if (budgeted > 0) {
                        double utilization = (double) projected / budgeted;
                        if (utilization >= 1.0) {
                            throw new BudgetThresholdExceededException(
                                "Budget threshold exceeded for account " + accountCode +
                                ": projected " + (int)(utilization * 100) + "% of budget");
                        } else if (utilization >= 0.8) {
                            log.warn("Budget warning for account {}: {}% utilized", accountCode, (int)(utilization * 100));
                        }
                    }
                }
            }
        }
    }

    @Override
    public com.bracit.fisprocess.dto.response.BudgetResponseDto getById(UUID tenantId, UUID id) {
        return budgetRepo.findByTenantIdAndId(tenantId, id)
            .map(b -> mapper.map(b, com.bracit.fisprocess.dto.response.BudgetResponseDto.class))
            .orElseThrow(() -> new BudgetNotFoundException(id));
    }

    @Override
    public Page<com.bracit.fisprocess.dto.response.BudgetResponseDto> list(UUID tenantId, Pageable pageable) {
        return budgetRepo.findByTenantId(tenantId, pageable)
            .map(b -> mapper.map(b, com.bracit.fisprocess.dto.response.BudgetResponseDto.class));
    }
}
