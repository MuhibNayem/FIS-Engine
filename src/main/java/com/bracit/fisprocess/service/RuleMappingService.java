package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.dto.request.FinancialEventRequestDto;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface RuleMappingService {

    DraftJournalEntry mapToDraft(UUID tenantId, FinancialEventRequestDto event, @Nullable String fallbackCreatedBy);
}
