package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.enums.ActorRole;

import java.time.LocalDate;
import java.util.UUID;

public interface PeriodValidationService {
    void validatePostingAllowed(UUID tenantId, LocalDate postedDate, ActorRole actorRole);
}
