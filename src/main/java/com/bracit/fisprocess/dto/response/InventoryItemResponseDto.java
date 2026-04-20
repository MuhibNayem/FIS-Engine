package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponseDto {
    private UUID id;
    private String tenantId;
    private String sku;
    private String name;
    private String category;
    private String uom;
    private String costMethod;
    private String glInventoryAccountCode;
    private String glCogsAccountCode;
    private Long quantityOnHand;
    private Long totalValue;
    private Long standardCost;
}