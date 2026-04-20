package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.*;
import com.bracit.fisprocess.dto.response.ConsolidationGroupResponseDto;
import com.bracit.fisprocess.dto.response.ConsolidationMemberResponseDto;
import com.bracit.fisprocess.dto.response.ConsolidationRunResponseDto;
import com.bracit.fisprocess.exception.ConsolidationGroupNotFoundException;
import com.bracit.fisprocess.repository.*;
import com.bracit.fisprocess.service.ConsolidationService;
import com.bracit.fisprocess.service.ExchangeRateService;
import com.bracit.fisprocess.service.ReportingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConsolidationServiceImpl implements ConsolidationService {

    private final ConsolidationGroupRepository groupRepo;
    private final ConsolidationMemberRepository memberRepo;
    private final ConsolidationRunRepository runRepo;
    private final EliminationRuleRepository eliminationRuleRepo;
    private final ReportingRepository reportingRepo;
    private final ExchangeRateService exchangeRateService;
    private final ModelMapper mapper;

    @Override
    @Transactional
    public ConsolidationGroupResponseDto createGroup(UUID tenantId,
            com.bracit.fisprocess.dto.request.CreateConsolidationGroupRequestDto req) {
        var g = mapper.map(req, ConsolidationGroup.class);
        g.setTenantId(tenantId);
        return mapper.map(groupRepo.save(g), ConsolidationGroupResponseDto.class);
    }

    @Override
    @Transactional
    public ConsolidationMemberResponseDto addMember(UUID groupId,
            com.bracit.fisprocess.dto.request.AddConsolidationMemberRequestDto req) {
        var group = groupRepo.findById(groupId)
            .orElseThrow(() -> new ConsolidationGroupNotFoundException(groupId));
        var m = mapper.map(req, ConsolidationMember.class);
        m.setGroupId(groupId);
        m.setTenantId(group.getTenantId());
        return mapper.map(memberRepo.save(m), ConsolidationMemberResponseDto.class);
    }

    @Override
    @Transactional
    public ConsolidationRunResponseDto run(UUID groupId, String period) {
        var group = groupRepo.findById(groupId)
            .orElseThrow(() -> new ConsolidationGroupNotFoundException(groupId));

        List<ConsolidationMember> members = memberRepo.findByGroupId(groupId);
        if (members.isEmpty()) {
            throw new RuntimeException("No members in consolidation group: " + groupId);
        }

        // Parse period "2026-01" to get end date
        YearMonth ym = YearMonth.parse(period);
        LocalDate asOfDate = ym.atEndOfMonth();

        // Map to accumulate consolidated balances by account code
        Map<String, ConsolidatedBalance> consolidated = new HashMap<>();
        long totalAssets = 0, totalLiabilities = 0, totalEquity = 0, netIncome = 0;

        // Step 1: Aggregate trial balances from all member tenants
        for (ConsolidationMember member : members) {
            // Query trial balance for this member tenant
            List<Map<String, Object>> tbLines = reportingRepo.findTrialBalanceLines(
                member.getMemberTenantId(), asOfDate);

            BigDecimal ownership = member.getOwnershipPercentage() != null
                ? member.getOwnershipPercentage()
                : BigDecimal.valueOf(100);

            for (Map<String, Object> line : tbLines) {
                String accountCode = (String) line.get("account_code");
                String accountType = (String) line.get("account_type");
                long totalDebits = ((Number) line.get("total_debits")).longValue();
                long totalCredits = ((Number) line.get("total_credits")).longValue();
                long netBalance = totalDebits - totalCredits;

                // Translate currency if needed
                if (!member.getCurrency().equals(group.getBaseCurrency())) {
                    BigDecimal rate = exchangeRateService.resolveRate(
                        member.getMemberTenantId(),
                        member.getCurrency(),
                        group.getBaseCurrency(),
                        asOfDate);
                    netBalance = BigDecimal.valueOf(netBalance)
                        .multiply(rate)
                        .longValue();
                }

                // Apply ownership percentage
                long finalAmount = BigDecimal.valueOf(netBalance)
                    .multiply(ownership)
                    .divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_HALF_UP)
                    .longValue();

                // Aggregate
                final long amountForAggregate = finalAmount;
                consolidated.compute(accountCode, (k, existing) -> {
                    if (existing == null) {
                        return new ConsolidatedBalance(accountCode, accountType, amountForAggregate);
                    } else {
                        existing.addAmount(amountForAggregate);
                        return existing;
                    }
                });
            }
        }

        // Step 2: Apply elimination rules
        List<EliminationRule> rules = eliminationRuleRepo.findByGroupIdAndIsActiveTrue(groupId);
        for (EliminationRule rule : rules) {
            ConsolidatedBalance from = consolidated.get(rule.getFromAccountCode());
            ConsolidatedBalance to = consolidated.get(rule.getToAccountCode());

            if (from != null && to != null) {
                long eliminationAmount = Math.min(Math.abs(from.getAmount()), Math.abs(to.getAmount()));
                from.addAmount(-eliminationAmount);
                to.addAmount(-eliminationAmount);
                log.info("Applied elimination rule: {}x{} eliminated", rule.getFromAccountCode(), rule.getToAccountCode());
            }
        }

        // Step 3: Calculate totals by account type
        for (ConsolidatedBalance balance : consolidated.values()) {
            long amount = Math.abs(balance.getAmount());
            switch (balance.getAccountType()) {
                case "ASSET" -> totalAssets += amount;
                case "LIABILITY" -> totalLiabilities += amount;
                case "EQUITY" -> totalEquity += amount;
                case "REVENUE", "EXPENSE" -> netIncome += balance.getAmount();
            }
        }

        // For net income calculation: Revenue - Expenses
        // netIncome already has the correct sign from the TB calculation

        // Step 4: Persist the run record
        var run = ConsolidationRun.builder()
            .tenantId(group.getTenantId())
            .groupId(groupId)
            .period(period)
            .runDate(LocalDate.now())
            .status("COMPLETED")
            .totalAssets(totalAssets)
            .totalLiabilities(totalLiabilities)
            .totalEquity(totalEquity)
            .netIncome(netIncome)
            .build();

        log.info("Completed consolidation run for group '{}' period '{}': assets={}, liabilities={}, equity={}, netIncome={}",
            group.getName(), period, totalAssets, totalLiabilities, totalEquity, netIncome);

        return mapper.map(runRepo.save(run), ConsolidationRunResponseDto.class);
    }

    @Override
    public ConsolidationGroupResponseDto getGroup(UUID tenantId, UUID id) {
        return groupRepo.findByTenantIdAndId(tenantId, id)
            .map(g -> mapper.map(g, ConsolidationGroupResponseDto.class))
            .orElseThrow(() -> new ConsolidationGroupNotFoundException(id));
    }

    @Override
    public Page<ConsolidationGroupResponseDto> listGroups(UUID tenantId, Pageable pageable) {
        return groupRepo.findByTenantId(tenantId, pageable)
            .map(g -> mapper.map(g, ConsolidationGroupResponseDto.class));
    }

    // Helper class to track consolidated balances
    private static class ConsolidatedBalance {
        private final String accountCode;
        private final String accountType;
        private long amount;

        ConsolidatedBalance(String accountCode, String accountType, long amount) {
            this.accountCode = accountCode;
            this.accountType = accountType;
            this.amount = amount;
        }

        void addAmount(long delta) {
            this.amount += delta;
        }

        String getAccountCode() { return accountCode; }
        String getAccountType() { return accountType; }
        long getAmount() { return amount; }
    }
}
