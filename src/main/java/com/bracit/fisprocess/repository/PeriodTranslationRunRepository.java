package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.PeriodTranslationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PeriodTranslationRunRepository extends JpaRepository<PeriodTranslationRun, UUID> {

    Optional<PeriodTranslationRun> findByTenantIdAndPeriodId(UUID tenantId, UUID periodId);
}
