package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.PayrollDeduction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PayrollDeductionRepository extends JpaRepository<PayrollDeduction, UUID> {
    Optional<PayrollDeduction> findByTenantIdAndId(UUID tenantId, UUID id);
    
}
