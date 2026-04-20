package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for outbox relay records.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<OutboxEvent> {

    List<OutboxEvent> findTop100ByPublishedFalseAndDlqFalseOrderByCreatedAtAsc();

    long countByPublishedFalseAndDlqFalse();

    long countByDlqTrue();

    @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.published = false AND o.dlq = false")
    Optional<OffsetDateTime> findOldestUnpublishedCreatedAt();

    @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.dlq = true")
    Optional<OffsetDateTime> findOldestDlqCreatedAt();

    long countByTenantIdAndEventType(UUID tenantId, String eventType);

    /**
     * Finds unpublished, non-DLQ events whose retry count has reached or
     * exceeded their max_retries threshold. These are candidates for DLQ.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false AND o.dlq = false AND o.retryCount >= o.maxRetries")
    List<OutboxEvent> findExhaustedRetryEvents();

    /**
     * Finds a single unpublished, non-DLQ event by its ID for admin retry.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.outboxId = :id AND o.published = false AND o.dlq = false")
    Optional<OutboxEvent> findActiveEventById(UUID id);

    /**
     * Finds DLQ events (paginated via Spring Data pagination at call site).
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.dlq = true ORDER BY o.createdAt DESC")
    List<OutboxEvent> findDlqEvents(org.springframework.data.domain.Pageable pageable);

    /**
     * Finds a DLQ event by its ID for admin retry/discard operations.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.outboxId = :id AND o.dlq = true")
    Optional<OutboxEvent> findDlqEventById(UUID id);

    /**
     * Finds unpublished, non-DLQ events ordered by creation date (paginated).
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false AND o.dlq = false ORDER BY o.createdAt ASC")
    org.springframework.data.domain.Page<OutboxEvent> findPendingEvents(
            org.springframework.data.domain.Pageable pageable);

    /**
     * Resets retry state on an event so it can be re-published.
     */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.retryCount = 0, o.dlq = false, o.lastError = NULL WHERE o.outboxId = :id")
    int resetRetryState(UUID id);

    /**
     * Marks an event as dead-lettered.
     */
    @Modifying
    @Query("UPDATE OutboxEvent o SET o.dlq = true, o.lastError = :error WHERE o.outboxId = :id")
    int markAsDlq(UUID id, String error);

    /**
     * Permanently deletes an event (used for DLQ discard).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.outboxId = :id")
    int deleteEventById(UUID id);

    /**
     * Deletes published outbox entries older than the specified cutoff.
     * Only published entries (published = true) are eligible for cleanup.
     *
     * @param cutoff the timestamp cutoff; entries created before this are deleted
     * @return the number of entries deleted
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.createdAt < :cutoff")
    int deletePublishedBefore(OffsetDateTime cutoff);
}
