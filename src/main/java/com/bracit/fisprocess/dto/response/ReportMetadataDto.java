package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Common metadata included in every report response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMetadataDto {

    private String reportType;
    private UUID tenantId;
    @Nullable
    private LocalDate asOfDate;
    @Nullable
    private LocalDate fromDate;
    @Nullable
    private LocalDate toDate;
    private String baseCurrency;
    private OffsetDateTime generatedAt;
}
