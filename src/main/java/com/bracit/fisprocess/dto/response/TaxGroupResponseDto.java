package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for Tax Group details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxGroupResponseDto {

    private UUID taxGroupId;
    private UUID tenantId;
    private String name;
    private String description;
    private List<TaxGroupRateResponseDto> rates;
    private OffsetDateTime createdAt;

    /**
     * Inner DTO for Tax Group Rate details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxGroupRateResponseDto {
        private UUID taxGroupRateId;
        private UUID taxRateId;
        private String taxRateCode;
        private Boolean isCompound;
    }
}
