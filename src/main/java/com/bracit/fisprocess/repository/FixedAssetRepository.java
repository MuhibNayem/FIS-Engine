package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.FixedAsset;
import com.bracit.fisprocess.domain.entity.FixedAsset.AssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {
    Optional<FixedAsset> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<FixedAsset> findByTenantId(UUID tenantId, Pageable pageable);
    Page<FixedAsset> findByTenantIdAndStatus(UUID tenantId, AssetStatus status, Pageable pageable);
    List<FixedAsset> findByTenantIdAndStatus(UUID tenantId, AssetStatus status);
    Page<FixedAsset> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId, Pageable pageable);
    List<FixedAsset> findByTenantIdAndCategoryId(UUID tenantId, UUID categoryId);
    List<FixedAsset> findByTenantIdAndCategoryIdAndStatus(UUID tenantId, UUID categoryId, AssetStatus status);
    List<FixedAsset> findByTenantIdAndStatusNot(UUID tenantId, AssetStatus status);
    Optional<FixedAsset> findByTenantIdAndAssetTag(UUID tenantId, String assetTag);
}
