package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.PayrollTax;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PayrollTaxRepository extends JpaRepository<PayrollTax, UUID> {
    Optional<PayrollTax> findByTenantIdAndId(UUID tenantId, UUID id);
    
}
