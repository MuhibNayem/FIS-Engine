package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for outbox relay records.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    long countByTenantIdAndEventType(UUID tenantId, String eventType);
}
