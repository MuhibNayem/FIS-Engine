package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalWorkflow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalWorkflowRepository extends JpaRepository<JournalWorkflow, UUID> {

    Optional<JournalWorkflow> findByTenantIdAndWorkflowId(UUID tenantId, UUID workflowId);

    @EntityGraph(attributePaths = "lines")
    Optional<JournalWorkflow> findWithLinesByTenantIdAndWorkflowId(UUID tenantId, UUID workflowId);

    boolean existsByTenantIdAndEventId(UUID tenantId, String eventId);
}
