package com.bracit.fisprocess.dto.request;

import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetRevaluationRequestDto {
    private long newValue;
    private String reason;
    private LocalDate date;
}