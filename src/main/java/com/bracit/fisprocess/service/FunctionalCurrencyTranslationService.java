package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.RunTranslationRequestDto;
import com.bracit.fisprocess.dto.response.TranslationResponseDto;

import java.util.UUID;

public interface FunctionalCurrencyTranslationService {

    TranslationResponseDto run(UUID tenantId, UUID periodId, RunTranslationRequestDto request);
}
