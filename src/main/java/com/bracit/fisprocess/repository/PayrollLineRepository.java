package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.PayrollLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID> {
    Optional<PayrollLine> findByTenantIdAndId(UUID tenantId, UUID id);
    List<PayrollLine> findByRunId(UUID runId);

    @Query("SELECT COALESCE(SUM(pl.incomeTax), 0) FROM PayrollLine pl WHERE pl.runId = :runId")
    Long sumIncomeTaxByRunId(@Param("runId") UUID runId);

    @Query("SELECT COALESCE(SUM(pl.socialSecurity), 0) FROM PayrollLine pl WHERE pl.runId = :runId")
    Long sumSocialSecurityByRunId(@Param("runId") UUID runId);
}
