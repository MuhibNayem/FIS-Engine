package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.OutboxEvent;
import com.bracit.fisprocess.repository.OutboxEventRepository;
import com.bracit.fisprocess.service.DeadLetterQueueService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of the dead-letter queue lifecycle for outbox events.
 * <p>
 * All operations are transactional and update the DLQ flag on the
 * {@link OutboxEvent} entity rather than moving rows to a separate table.
 * This keeps referential integrity intact and simplifies admin operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueServiceImpl implements DeadLetterQueueService {

    private static final int MAX_ERROR_LENGTH = 2048;

    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;

    private Counter dlqMoveCounter;
    private Counter dlqRetryCounter;
    private Counter dlqDiscardCounter;

    @PostConstruct
    void initMetrics() {
        dlqMoveCounter = meterRegistry.counter("fis.outbox.dlq.move.count");
        dlqRetryCounter = meterRegistry.counter("fis.outbox.dlq.retry.count");
        dlqDiscardCounter = meterRegistry.counter("fis.outbox.dlq.discard.count");
    }

    @Override
    @Transactional
    public void moveToDlq(UUID eventId, @Nullable String error) {
        String truncatedError = error == null ? null
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH)
                : error;
        int updated = outboxEventRepository.markAsDlq(eventId, truncatedError);
        if (updated > 0) {
            dlqMoveCounter.increment();
            log.warn("Outbox event outboxId='{}' moved to DLQ. Error: {}", eventId, truncatedError);
        } else {
            log.warn("Attempted to move non-existent outbox event outboxId='{}' to DLQ", eventId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OutboxEvent> listDlq(Pageable pageable) {
        return outboxEventRepository.findAll((root, query, cb) -> cb.equal(root.get("dlq"), true), pageable);
    }

    @Override
    @Transactional
    public boolean retryFromDlq(UUID eventId) {
        int updated = outboxEventRepository.resetRetryState(eventId);
        if (updated > 0) {
            dlqRetryCounter.increment();
            log.info("DLQ event outboxId='{}' reset for retry", eventId);
            return true;
        }
        log.warn("Attempted to retry non-existent DLQ event outboxId='{}'", eventId);
        return false;
    }

    @Override
    @Transactional
    public boolean discardFromDlq(UUID eventId) {
        int deleted = outboxEventRepository.deleteEventById(eventId);
        if (deleted > 0) {
            dlqDiscardCounter.increment();
            log.info("DLQ event outboxId='{}' permanently discarded", eventId);
            return true;
        }
        log.warn("Attempted to discard non-existent DLQ event outboxId='{}'", eventId);
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public long dlqSize() {
        return outboxEventRepository.countByDlqTrue();
    }
}
