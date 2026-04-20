package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovementResponseDto {
    private UUID movementId;
    private UUID tenantId;
    private UUID itemId;
    private UUID warehouseId;
    private String type;
    private Long quantity;
    private Long unitCost;
    private Long totalCost;
    private String reference;
    private LocalDate referenceDate;
}