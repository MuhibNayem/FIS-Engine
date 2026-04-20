package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.IdempotencyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
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

    /**
     * Deletes idempotency log entries created before the given cutoff date.
     * Used by the scheduled cleanup job to prevent unbounded table growth.
     *
     * @param createdAtCutoff the cutoff timestamp (entries before this are deleted)
     * @return the number of deleted entries
     */
    @Modifying
    @Query("DELETE FROM IdempotencyLog i WHERE i.createdAt < :createdAtCutoff")
    int deleteBeforeCreatedAt(@Param("createdAtCutoff") OffsetDateTime createdAtCutoff);
}
