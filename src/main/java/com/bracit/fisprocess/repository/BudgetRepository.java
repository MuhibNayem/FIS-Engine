package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.Budget;
import com.bracit.fisprocess.domain.enums.BudgetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    Optional<Budget> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Budget> findByTenantId(UUID tenantId, Pageable pageable);
    List<Budget> findByTenantIdAndFiscalYearAndStatus(UUID tenantId, Integer fiscalYear, BudgetStatus status);
}
