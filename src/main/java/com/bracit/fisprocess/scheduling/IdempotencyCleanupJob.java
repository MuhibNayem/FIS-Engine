package com.bracit.fisprocess.scheduling;

import com.bracit.fisprocess.repository.IdempotencyLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Scheduled job that purges stale idempotency log entries older than the
 * configured retention period.
 * <p>
 * This prevents unbounded growth of the idempotency log table in PostgreSQL.
 * Only entries older than the retention period are deleted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupJob {

    private final IdempotencyLogRepository idempotencyLogRepository;
    private final MeterRegistry meterRegistry;
    private Counter cleanupDeletedCounter;

    @Value("${fis.idempotency.retention-days:7}")
    private int retentionDays;

    /**
     * Runs daily at 03:00 AM to purge stale idempotency log entries.
     */
    @Scheduled(cron = "${fis.idempotency.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeStaleEntries() {
        if (cleanupDeletedCounter == null) {
            cleanupDeletedCounter = meterRegistry.counter("fis.idempotency.cleanup.deleted.count");
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = idempotencyLogRepository.deleteBeforeCreatedAt(cutoff);
        if (deleted > 0) {
            cleanupDeletedCounter.increment(deleted);
        }

        if (deleted > 0) {
            log.info("Idempotency cleanup: deleted {} entries older than {} days (cutoff: {})",
                    deleted, retentionDays, cutoff);
        } else {
            log.debug("Idempotency cleanup: no entries to purge (retention={} days, cutoff={})",
                    retentionDays, cutoff);
        }
    }
}
