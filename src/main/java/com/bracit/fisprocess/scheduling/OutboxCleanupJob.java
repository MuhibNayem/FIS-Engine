package com.bracit.fisprocess.scheduling;

import com.bracit.fisprocess.repository.OutboxEventRepository;
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
 * Scheduled job that purges published outbox entries older than the
 * configured retention period.
 * <p>
 * Only entries with {@code published = true} are eligible for deletion.
 * Unpublished entries are never deleted, regardless of age.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;
    private Counter cleanupDeletedCounter;

    @Value("${fis.outbox.retention-days:30}")
    private int retentionDays;

    /**
     * Runs daily at 02:00 AM to purge stale published outbox entries.
     */
    @Scheduled(cron = "${fis.outbox.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void purgePublishedEntries() {
        if (cleanupDeletedCounter == null) {
            cleanupDeletedCounter = meterRegistry.counter("fis.outbox.cleanup.deleted.count");
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            cleanupDeletedCounter.increment(deleted);
        }

        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} published entries older than {} days (cutoff: {})",
                    deleted, retentionDays, cutoff);
        } else {
            log.debug("Outbox cleanup: no entries to purge (retention={} days, cutoff={})",
                    retentionDays, cutoff);
        }
    }
}
