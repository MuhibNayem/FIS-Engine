package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import com.bracit.fisprocess.domain.enums.InventoryMovementType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fis_inventorymovement")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovement {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private InventoryMovementType type;

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "unit_cost")
    private Long unitCost;

    @Column(name = "total_cost")
    private Long totalCost;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}