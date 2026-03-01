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
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    long countByPublishedFalse();

    @Query("SELECT MIN(o.createdAt) FROM OutboxEvent o WHERE o.published = false")
    Optional<OffsetDateTime> findOldestUnpublishedCreatedAt();

    long countByTenantIdAndEventType(UUID tenantId, String eventType);

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
