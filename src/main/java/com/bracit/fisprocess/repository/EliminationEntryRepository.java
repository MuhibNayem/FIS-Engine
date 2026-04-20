package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.EliminationEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EliminationEntryRepository extends JpaRepository<EliminationEntry, UUID> {
    Optional<EliminationEntry> findByTenantIdAndId(UUID tenantId, UUID id);
    List<EliminationEntry> findByRunId(UUID runId);
}
