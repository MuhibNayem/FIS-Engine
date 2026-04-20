package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.PayrollRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
    Optional<PayrollRun> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<PayrollRun> findByTenantIdAndPeriod(UUID tenantId, String period);
}
