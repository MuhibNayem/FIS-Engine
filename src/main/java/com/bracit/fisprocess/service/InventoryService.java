package com.bracit.fisprocess.service;
import java.util.UUID;
public interface InventoryService {
    com.bracit.fisprocess.dto.response.WarehouseResponseDto createWarehouse(UUID tenantId, com.bracit.fisprocess.dto.request.CreateWarehouseRequestDto req);
    com.bracit.fisprocess.dto.response.InventoryItemResponseDto createItem(UUID tenantId, com.bracit.fisprocess.dto.request.CreateInventoryItemRequestDto req);
    com.bracit.fisprocess.dto.response.InventoryMovementResponseDto recordMovement(UUID tenantId, com.bracit.fisprocess.dto.request.RecordInventoryMovementRequestDto req);
    com.bracit.fisprocess.dto.response.InventoryValuationResponseDto getValuation(UUID tenantId, String period);
    com.bracit.fisprocess.dto.response.WarehouseResponseDto getWarehouse(UUID tenantId, UUID id);
    com.bracit.fisprocess.dto.response.InventoryItemResponseDto getItem(UUID tenantId, UUID id);
}
