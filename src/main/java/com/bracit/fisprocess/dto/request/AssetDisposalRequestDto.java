package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDisposalRequestDto {

    @NotNull(message = "Disposal date is required")
    private LocalDate disposalDate;

    private Long saleProceeds;

    private String disposalType;
}