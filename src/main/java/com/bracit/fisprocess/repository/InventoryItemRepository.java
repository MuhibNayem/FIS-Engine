package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<InventoryItem> findByTenantIdAndSku(UUID tenantId, String sku);
    List<InventoryItem> findByTenantId(UUID tenantId);
}
