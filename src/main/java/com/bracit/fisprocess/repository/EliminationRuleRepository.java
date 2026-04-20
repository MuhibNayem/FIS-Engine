package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.EliminationRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface EliminationRuleRepository extends JpaRepository<EliminationRule, UUID> {
    Optional<EliminationRule> findByTenantIdAndId(UUID tenantId, UUID id);
    List<EliminationRule> findByGroupIdAndIsActiveTrue(UUID groupId);
}
