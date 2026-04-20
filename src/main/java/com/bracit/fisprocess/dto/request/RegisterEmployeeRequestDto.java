package com.bracit.fisprocess.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterEmployeeRequestDto {

    @NotBlank(message = "Employee code is required")
    private String code;

    @NotBlank(message = "Employee name is required")
    private String name;

    private String department;

    private String glDepartmentAccountCode;

    @NotNull(message = "Basic salary is required")
    @Positive(message = "Basic salary must be positive")
    private Long basicSalary;

    private Long allowances;
}