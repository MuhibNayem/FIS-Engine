package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.BudgetLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface BudgetLineRepository extends JpaRepository<BudgetLine, UUID> {
    Optional<BudgetLine> findByTenantIdAndId(UUID tenantId, UUID id);
    List<BudgetLine> findByBudgetId(UUID budgetId);
    List<BudgetLine> findByBudgetIdAndAccountCode(UUID budgetId, String accountCode);
    Optional<BudgetLine> findByBudgetIdAndAccountCodeAndMonth(UUID budgetId, String accountCode, String month);
}
