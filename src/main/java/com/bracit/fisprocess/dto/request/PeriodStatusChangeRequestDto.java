package com.bracit.fisprocess.dto.request;

import com.bracit.fisprocess.domain.enums.PeriodStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodStatusChangeRequestDto {

    @NotNull(message = "status is required")
    private PeriodStatus status;
}
