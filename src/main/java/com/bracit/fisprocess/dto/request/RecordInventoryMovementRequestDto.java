package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.InventoryMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class RecordInventoryMovementRequestDto {

    @NotNull(message = "Item ID is required")
    private UUID itemId;

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    @NotNull(message = "Movement type is required")
    private InventoryMovementType type;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Long quantity;

    private Long unitCost;

    private String reference;

    private LocalDate referenceDate;
}