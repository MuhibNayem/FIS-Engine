package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.ConsolidationGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface ConsolidationGroupRepository extends JpaRepository<ConsolidationGroup, UUID> {
    Optional<ConsolidationGroup> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<ConsolidationGroup> findByTenantId(UUID tenantId, Pageable pageable);
}
