package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.CorrectionRequestDto;
import com.bracit.fisprocess.dto.request.ReversalRequestDto;
import com.bracit.fisprocess.dto.response.ReversalResponseDto;

import java.util.UUID;

public interface JournalReversalService {

    ReversalResponseDto reverse(UUID tenantId, UUID originalJournalEntryId, ReversalRequestDto request);

    ReversalResponseDto correct(UUID tenantId, UUID originalJournalEntryId, CorrectionRequestDto request);
}
