package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Employee> findByTenantIdAndCode(UUID tenantId, String code);
    Page<Employee> findByTenantId(UUID tenantId, Pageable pageable);
}
