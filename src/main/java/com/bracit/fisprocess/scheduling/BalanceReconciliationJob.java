package com.bracit.fisprocess.scheduling;

import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.service.impl.DerivedBalanceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceReconciliationJob {

    private final BusinessEntityRepository businessEntityRepository;
    private final DerivedBalanceService derivedBalanceService;
    private final MeterRegistry meterRegistry;

    private final AtomicLong lastSyncTimestamp = new AtomicLong(0);
    private final AtomicLong accountsSynced = new AtomicLong(0);
    private final AtomicLong discrepanciesFound = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${fis.balance.reconciliation.interval-ms:3600000}")
    public void reconcileAllTenants() {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            long startTime = System.currentTimeMillis();
            int tenantCount = 0;
            int totalAccounts = 0;

            for (UUID tenantId : businessEntityRepository.findAllIds()) {
                int synced = reconcileTenant(tenantId);
                totalAccounts += synced;
                tenantCount++;
            }

            long duration = System.currentTimeMillis() - startTime;
            lastSyncTimestamp.set(System.currentTimeMillis());

            meterRegistry.gauge("fis.balance.reconciliation.last_timestamp", lastSyncTimestamp);
            meterRegistry.gauge("fis.balance.reconciliation.accounts_synced", accountsSynced);
            meterRegistry.gauge("fis.balance.reconciliation.discrepancies", discrepanciesFound);

            log.info("Balance reconciliation completed: tenants={}, accounts={}, duration={}ms",
                    tenantCount, totalAccounts, duration);

        } catch (Exception e) {
            log.error("Balance reconciliation failed", e);
            meterRegistry.counter("fis.balance.reconciliation.error").increment();
        } finally {
            sample.stop(Timer.builder("fis.balance.reconciliation.duration")
                    .register(meterRegistry));
        }
    }

    public int reconcileTenant(UUID tenantId) {
        try {
            derivedBalanceService.syncAllAccountBalances(tenantId);
            accountsSynced.addAndGet(countTenantAccounts(tenantId));
            return countTenantAccounts(tenantId);
        } catch (Exception e) {
            log.error("Failed to reconcile balances for tenant {}", tenantId, e);
            return 0;
        }
    }

    private int countTenantAccounts(UUID tenantId) {
        return (int) businessEntityRepository.countAccountsByTenantId(tenantId);
    }

    public long getLastSyncTimestamp() {
        return lastSyncTimestamp.get();
    }

    public long getAccountsSynced() {
        return accountsSynced.get();
    }

    public long getDiscrepanciesFound() {
        return discrepanciesFound.get();
    }
}
