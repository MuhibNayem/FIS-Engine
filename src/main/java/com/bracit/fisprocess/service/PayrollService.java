package com.bracit.fisprocess.service;
import java.util.UUID;
public interface PayrollService {
    com.bracit.fisprocess.dto.response.EmployeeResponseDto registerEmployee(UUID tenantId, com.bracit.fisprocess.dto.request.RegisterEmployeeRequestDto req);
    com.bracit.fisprocess.dto.response.PayrollRunResponseDto createRun(UUID tenantId, com.bracit.fisprocess.dto.request.CreatePayrollRunRequestDto req, String performedBy);
    com.bracit.fisprocess.dto.response.PayrollRunResponseDto calculateAndPost(UUID tenantId, UUID runId, String performedBy);
    com.bracit.fisprocess.dto.response.PayrollRunResponseDto getById(UUID tenantId, UUID id);
    org.springframework.data.domain.Page<com.bracit.fisprocess.dto.response.EmployeeResponseDto> listEmployees(UUID tenantId, org.springframework.data.domain.Pageable pageable);
}
