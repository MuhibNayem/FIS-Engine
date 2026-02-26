package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for tenant accounting periods.
 */
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE p.tenantId = :tenantId
              AND p.startDate <= :date
              AND p.endDate >= :date
            """)
    Optional<AccountingPeriod> findContainingDate(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE p.tenantId = :tenantId
              AND p.startDate <= :endDate
              AND p.endDate >= :startDate
            """)
    List<AccountingPeriod> findOverlapping(
            @Param("tenantId") UUID tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    List<AccountingPeriod> findByTenantIdOrderByStartDateAsc(UUID tenantId);

    List<AccountingPeriod> findByTenantIdAndStatusOrderByStartDateAsc(UUID tenantId, PeriodStatus status);

    List<AccountingPeriod> findByTenantIdAndStartDateAfterOrderByStartDateAsc(UUID tenantId, LocalDate startDate);

    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE p.tenantId = :tenantId
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.startDate ASC
            """)
    List<AccountingPeriod> listByTenantAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") @Nullable PeriodStatus status);
}
