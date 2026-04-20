package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {
    Optional<BankStatement> findByTenantIdAndId(UUID tenantId, UUID id);
    @Query("SELECT s FROM BankStatement s LEFT JOIN FETCH s.lines WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<BankStatement> findWithLinesByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
    List<BankStatement> findByTenantIdAndBankAccountId(UUID tenantId, UUID bankAccountId);

    @Query("SELECT s FROM BankStatement s WHERE s.tenantId = :tenantId AND s.bankAccountId = :bankAccountId " +
           "AND s.statementDate BETWEEN :startDate AND :endDate ORDER BY s.statementDate DESC")
    List<BankStatement> findByTenantIdAndBankAccountIdAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("bankAccountId") UUID bankAccountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    @Query("SELECT s FROM BankStatement s WHERE s.tenantId = :tenantId AND (:bankAccountId IS NULL OR s.bankAccountId = :bankAccountId)")
    Page<BankStatement> findByTenantIdWithFilters(@Param("tenantId") UUID tenantId, @Param("bankAccountId") UUID bankAccountId, Pageable pageable);
}
