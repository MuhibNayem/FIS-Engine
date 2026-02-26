package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.MappingRule;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MappingRuleRepository extends JpaRepository<MappingRule, UUID> {

    @EntityGraph(attributePaths = "lines")
    Optional<MappingRule> findByTenantIdAndEventTypeAndIsActiveTrue(UUID tenantId, String eventType);

    @EntityGraph(attributePaths = "lines")
    Optional<MappingRule> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            SELECT mr FROM MappingRule mr
            WHERE mr.tenantId = :tenantId
              AND (:eventType IS NULL OR mr.eventType = :eventType)
              AND (:isActive IS NULL OR mr.isActive = :isActive)
            ORDER BY mr.updatedAt DESC
            """)
    Page<MappingRule> findByFilters(
            @Param("tenantId") UUID tenantId,
            @Param("eventType") @Nullable String eventType,
            @Param("isActive") @Nullable Boolean isActive,
            Pageable pageable);
}
