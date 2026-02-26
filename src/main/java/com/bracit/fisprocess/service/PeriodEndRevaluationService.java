package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.RunRevaluationRequestDto;
import com.bracit.fisprocess.dto.response.RevaluationResponseDto;

import java.util.UUID;

public interface PeriodEndRevaluationService {

    RevaluationResponseDto run(UUID tenantId, UUID periodId, RunRevaluationRequestDto request);
}
