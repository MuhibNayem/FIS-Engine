package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.response.LedgerIntegrityCheckResponseDto;

import java.util.UUID;

public interface LedgerIntegrityService {

    LedgerIntegrityCheckResponseDto checkTenant(UUID tenantId);
}
