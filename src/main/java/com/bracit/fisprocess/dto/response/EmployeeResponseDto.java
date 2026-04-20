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
public class EmployeeResponseDto {
    private UUID id;
    private String tenantId;
    private String code;
    private String name;
    private String department;
    private String glDepartmentAccountCode;
    private Long basicSalary;
    private Long allowances;
    private String status;
}