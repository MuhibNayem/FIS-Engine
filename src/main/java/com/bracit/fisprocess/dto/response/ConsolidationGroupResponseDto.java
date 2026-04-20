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
public class ConsolidationGroupResponseDto {
    private UUID id;
    private String tenantId;
    private String name;
    private String description;
    private String baseCurrency;
}