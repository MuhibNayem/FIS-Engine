package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.response.LedgerIntegrityCheckResponseDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.LedgerIntegrityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerIntegrityServiceImpl implements LedgerIntegrityService {

    private final AccountRepository accountRepository;
    private final BusinessEntityRepository businessEntityRepository;

    @Override
    @Transactional(readOnly = true)
    public LedgerIntegrityCheckResponseDto checkTenant(UUID tenantId) {
        Map<AccountType, Long> totals = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            totals.put(type, 0L);
        }

        for (Object[] row : accountRepository.sumBalancesByType(tenantId)) {
            AccountType type = AccountType.valueOf(String.valueOf(row[0]));
            long sum = ((Number) row[1]).longValue();
            totals.put(type, sum);
        }

        long asset = totals.get(AccountType.ASSET);
        long liability = totals.get(AccountType.LIABILITY);
        long equity = totals.get(AccountType.EQUITY);
        long revenue = totals.get(AccountType.REVENUE);
        long expense = totals.get(AccountType.EXPENSE);

        long equationDelta = asset - liability - equity - revenue + expense;
        boolean balanced = equationDelta == 0L;

        return LedgerIntegrityCheckResponseDto.builder()
                .tenantId(tenantId)
                .assetTotal(asset)
                .liabilityTotal(liability)
                .equityTotal(equity)
                .revenueTotal(revenue)
                .expenseTotal(expense)
                .equationDelta(equationDelta)
                .balanced(balanced)
                .build();
    }

    @Scheduled(fixedDelayString = "${fis.integrity.check-delay-ms:3600000}")
    @Transactional(readOnly = true)
    public void scheduledIntegrityCheck() {
        businessEntityRepository.findAll().stream()
                .filter(be -> be.isActive())
                .forEach(tenant -> {
                    LedgerIntegrityCheckResponseDto result = checkTenant(tenant.getTenantId());
                    if (!result.isBalanced()) {
                        log.warn("Ledger integrity violation tenant='{}' delta={} asset={} liability={} equity={} revenue={} expense={}",
                                tenant.getTenantId(),
                                result.getEquationDelta(),
                                result.getAssetTotal(),
                                result.getLiabilityTotal(),
                                result.getEquityTotal(),
                                result.getRevenueTotal(),
                                result.getExpenseTotal());
                    } else {
                        log.debug("Ledger integrity check passed for tenant='{}'", tenant.getTenantId());
                    }
                });
    }
}
