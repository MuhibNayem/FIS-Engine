package com.bracit.fisprocess.service;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Manages the dead-letter queue lifecycle for outbox events.
 * <p>
 * Provides operations to move exhausted events into DLQ state,
 * list them, retry them, or permanently discard them.
 */
public interface DeadLetterQueueService {

    /**
     * Moves an outbox event to the dead-letter queue after its retry
     * budget has been exhausted.
     *
     * @param eventId the outbox event ID
     * @param error   the last error message (truncated if necessary)
     */
    void moveToDlq(UUID eventId, @Nullable String error);

    /**
     * Lists all events currently in the DLQ (paginated).
     */
    Page<com.bracit.fisprocess.domain.entity.OutboxEvent> listDlq(Pageable pageable);

    /**
     * Resets an event's retry state so it can be re-published.
     * Removes the DLQ flag and resets the retry counter.
     *
     * @param eventId the DLQ event ID
     * @return {@code true} if the event was found and reset
     */
    boolean retryFromDlq(UUID eventId);

    /**
     * Permanently discards (deletes) a DLQ event.
     *
     * @param eventId the DLQ event ID
     * @return {@code true} if the event was found and deleted
     */
    boolean discardFromDlq(UUID eventId);

    /**
     * Returns the total number of events in the DLQ.
     */
    long dlqSize();
}
