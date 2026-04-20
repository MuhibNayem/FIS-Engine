package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalWorkflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalWorkflowRepository extends JpaRepository<JournalWorkflow, UUID> {

    Optional<JournalWorkflow> findByTenantIdAndWorkflowId(UUID tenantId, UUID workflowId);

    @EntityGraph(attributePaths = "lines")
    Optional<JournalWorkflow> findWithLinesByTenantIdAndWorkflowId(UUID tenantId, UUID workflowId);

    /**
     * Retrieves workflow with lines and acquires a pessimistic write lock
     * to prevent concurrent approval/rejection race conditions.
     */
    @EntityGraph(attributePaths = "lines")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT jw FROM JournalWorkflow jw
            WHERE jw.tenantId = :tenantId AND jw.workflowId = :workflowId
            """)
    Optional<JournalWorkflow> findWithLinesForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("workflowId") UUID workflowId);

    boolean existsByTenantIdAndEventId(UUID tenantId, String eventId);

    @Query("""
            SELECT jw.eventId
            FROM JournalWorkflow jw
            WHERE jw.tenantId = :tenantId
              AND jw.eventId IN :eventIds
            """)
    List<String> findExistingEventIds(
            @Param("tenantId") UUID tenantId,
            @Param("eventIds") List<String> eventIds);
}
