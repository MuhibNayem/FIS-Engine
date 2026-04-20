package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.AssetDepreciationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface AssetDepreciationRunRepository extends JpaRepository<AssetDepreciationRun, UUID> {
    Optional<AssetDepreciationRun> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<AssetDepreciationRun> findByTenantIdAndPeriod(UUID tenantId, String period);
    List<AssetDepreciationRun> findByTenantIdAndPeriodBetween(UUID tenantId, String periodFrom, String periodTo);
    List<AssetDepreciationRun> findByTenantId(UUID tenantId);
}
