package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.PeriodRevaluationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeriodRevaluationRunRepository extends JpaRepository<PeriodRevaluationRun, UUID> {

    Optional<PeriodRevaluationRun> findByTenantIdAndPeriodId(UUID tenantId, UUID periodId);
}
