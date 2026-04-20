package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.ConsolidationMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ConsolidationMemberRepository extends JpaRepository<ConsolidationMember, UUID> {
    Optional<ConsolidationMember> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ConsolidationMember> findByGroupId(UUID groupId);
}
