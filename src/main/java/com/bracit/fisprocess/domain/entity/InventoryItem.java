package com.bracit.fisprocess.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import com.bracit.fisprocess.domain.enums.InventoryValuationMethod;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fis_inventoryitem", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "sku"}))
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "uom", length = 20)
    @Builder.Default
    private String uom = "EA";

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_method", nullable = false, length = 20)
    @Builder.Default
    private InventoryValuationMethod costMethod = InventoryValuationMethod.FIFO;

    @Column(name = "gl_inventory_account_code", length = 50)
    private String glInventoryAccountCode;

    @Column(name = "gl_cogs_account_code", length = 50)
    private String glCogsAccountCode;

    @Column(name = "quantity_on_hand")
    private Long quantityOnHand;

    @Column(name = "total_value")
    private Long totalValue;

    @Column(name = "standard_cost")
    private Long standardCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}