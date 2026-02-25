package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for durable idempotency records.
 */
public interface IdempotencyLogRepository
        extends JpaRepository<IdempotencyLog, IdempotencyLog.IdempotencyLogId> {

    Optional<IdempotencyLog> findByTenantIdAndEventId(UUID tenantId, String eventId);
}
