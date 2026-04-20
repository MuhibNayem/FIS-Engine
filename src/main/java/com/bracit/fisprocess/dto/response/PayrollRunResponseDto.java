package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRunResponseDto {
    private UUID runId;
    private String tenantId;
    private String period;
    private LocalDate runDate;
    private Long totalGross;
    private Long totalDeductions;
    private Long totalNet;
    private String status;
    private String approvedBy;
    private String createdBy;
}