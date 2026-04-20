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
public class WarehouseResponseDto {
    private UUID id;
    private String code;
    private String name;
    private String location;
    private String glAccountCode;
    private String tenantId;
}