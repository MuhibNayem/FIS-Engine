package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Repository for FX rates.
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    Optional<ExchangeRate> findByTenantIdAndSourceCurrencyAndTargetCurrencyAndEffectiveDate(
            UUID tenantId, String sourceCurrency, String targetCurrency, LocalDate effectiveDate);

    @Query("""
            SELECT r FROM ExchangeRate r
            WHERE r.tenantId = :tenantId
              AND r.sourceCurrency = :sourceCurrency
              AND r.targetCurrency = :targetCurrency
              AND r.effectiveDate <= :effectiveDate
            ORDER BY r.effectiveDate DESC
            """)
    List<ExchangeRate> findLatestPrior(
            @Param("tenantId") UUID tenantId,
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("effectiveDate") LocalDate effectiveDate);

    @Query("""
            SELECT r FROM ExchangeRate r
            WHERE r.tenantId = :tenantId
              AND r.sourceCurrency = :sourceCurrency
              AND r.targetCurrency = :targetCurrency
              AND (:effectiveDate IS NULL OR r.effectiveDate = :effectiveDate)
            ORDER BY r.effectiveDate DESC
            """)
    List<ExchangeRate> query(
            @Param("tenantId") UUID tenantId,
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("effectiveDate") LocalDate effectiveDate);

    @Query("""
            SELECT AVG(r.rate)
            FROM ExchangeRate r
            WHERE r.tenantId = :tenantId
              AND r.sourceCurrency = :sourceCurrency
              AND r.targetCurrency = :targetCurrency
              AND r.effectiveDate BETWEEN :fromDate AND :toDate
            """)
    Optional<BigDecimal> findAverageRateInRange(
            @Param("tenantId") UUID tenantId,
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
