package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotNull;
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
public class AddConsolidationMemberRequestDto {

    @NotNull(message = "Member tenant ID is required")
    private UUID memberTenantId;

    private BigDecimal ownershipPercentage;

    private String currency;

    private String translationMethod;
}