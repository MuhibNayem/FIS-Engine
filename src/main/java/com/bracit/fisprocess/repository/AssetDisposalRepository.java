package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.AssetDisposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, UUID> {
    Optional<AssetDisposal> findByTenantIdAndId(UUID tenantId, UUID id);
    List<AssetDisposal> findByTenantIdAndDisposalDateBetween(UUID tenantId, LocalDate from, LocalDate to);
    List<AssetDisposal> findByTenantId(UUID tenantId);
}
