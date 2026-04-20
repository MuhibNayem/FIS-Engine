package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.BankAccount;
import com.bracit.fisprocess.domain.enums.BankAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
    Optional<BankAccount> findByTenantIdAndId(UUID tenantId, UUID id);
    boolean existsByTenantIdAndAccountNumber(UUID tenantId, String accountNumber);
    @Query("SELECT b FROM BankAccount b WHERE b.tenantId = :tenantId AND (:status IS NULL OR b.status = :status)")
    Page<BankAccount> findByTenantIdWithFilters(@Param("tenantId") UUID tenantId, @Param("status") BankAccountStatus status, Pageable pageable);
}
