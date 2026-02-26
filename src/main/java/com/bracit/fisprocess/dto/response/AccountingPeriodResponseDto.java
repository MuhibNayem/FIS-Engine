package com.bracit.fisprocess.dto.response;

import com.bracit.fisprocess.domain.enums.PeriodStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingPeriodResponseDto {
    private UUID periodId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodStatus status;
    @Nullable
    private String closedBy;
    @Nullable
    private OffsetDateTime closedAt;
}
