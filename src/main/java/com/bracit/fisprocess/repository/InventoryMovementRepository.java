package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.InventoryMovement;
import com.bracit.fisprocess.domain.enums.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    Optional<InventoryMovement> findByTenantIdAndId(UUID tenantId, UUID id);
    List<InventoryMovement> findByItemIdAndWarehouseId(UUID itemId, UUID warehouseId);
    List<InventoryMovement> findByItemIdAndTypeOrderByReferenceDateAsc(UUID itemId, InventoryMovementType type);
}
