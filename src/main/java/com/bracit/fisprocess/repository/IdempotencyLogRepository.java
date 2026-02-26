package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for durable idempotency records.
 */
public interface IdempotencyLogRepository
        extends JpaRepository<IdempotencyLog, IdempotencyLog.IdempotencyLogId> {

    Optional<IdempotencyLog> findByTenantIdAndEventId(UUID tenantId, String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyLog i WHERE i.tenantId = :tenantId AND i.eventId = :eventId")
    Optional<IdempotencyLog> findByTenantIdAndEventIdForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("eventId") String eventId);
}
