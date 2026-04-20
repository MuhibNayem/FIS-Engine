package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryValuationResponseDto {
    private String period;
    private Long totalValue;

    @Builder.Default
    private List<InventoryValuationItemDto> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryValuationItemDto {
        private String sku;
        private String name;
        private Long quantity;
        private Long unitCost;
        private Long totalValue;
    }
}