package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.AssetCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {
    Optional<AssetCategory> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<AssetCategory> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<AssetCategory> findByTenantIdAndName(UUID tenantId, String name);
}
