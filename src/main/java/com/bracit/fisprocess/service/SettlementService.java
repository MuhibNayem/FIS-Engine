package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.SettlementRequestDto;
import com.bracit.fisprocess.dto.response.SettlementResponseDto;

import java.util.UUID;

public interface SettlementService {

    SettlementResponseDto settle(UUID tenantId, SettlementRequestDto request);
}
