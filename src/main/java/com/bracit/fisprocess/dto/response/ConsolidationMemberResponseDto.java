package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsolidationMemberResponseDto {
    private UUID id;
    private UUID groupId;
    private UUID memberTenantId;
    private BigDecimal ownershipPercentage;
    private String currency;
    private String translationMethod;
}